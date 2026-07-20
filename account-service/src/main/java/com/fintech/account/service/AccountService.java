package com.fintech.account.service;

import com.fintech.account.client.UserDirectoryClient;
import com.fintech.account.entity.Account;
import com.fintech.account.entity.FundReservation;
import com.fintech.account.entity.FundReservationStatus;
import com.fintech.account.dto.InternalBeneficiaryResponse;
import com.fintech.account.repository.AccountRepository;
import com.fintech.account.repository.FundReservationRepository;
import com.fintech.common.dto.internal.AccountSnapshot;
import com.fintech.common.dto.internal.UserSnapshot;
import com.fintech.common.enums.AccountStatus;
import com.fintech.common.enums.AccountType;
import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.enums.TransferRail;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.exception.AccountFrozenException;
import com.fintech.common.exception.ForbiddenException;
import com.fintech.common.exception.InsufficientBalanceException;
import com.fintech.common.exception.ResourceNotFoundException;
import com.fintech.common.exception.BusinessException;
import com.fintech.common.util.IbanUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Istanbul");

    private final AccountRepository accountRepository;
    private final FundReservationRepository fundReservationRepository;
    private final LedgerService ledgerService;
    private final UserDirectoryClient userDirectoryClient;

    /**
     * Yeni hesap oluştur.
     */
    @Transactional
    public Account createAccount(Long userId, String accountName, AccountType accountType,
                                 Currency currency) {
        // IBAN benzeri hesap numarası üret
        String accountNumber = generateAccountNumber();

        Account account = Account.builder()
                .userId(userId)
                .accountNumber(accountNumber)
                .accountName(accountName != null ? accountName : getDefaultAccountName(accountType, currency))
                .accountType(accountType)
                .currency(currency)
                .balance(BigDecimal.ZERO)
                .reservedBalance(BigDecimal.ZERO)
                .dailyLimit(getDefaultDailyLimit(accountType))
                .dailySpent(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Yeni hesap oluşturuldu - userId: {}, accountNumber: {}, type: {}, currency: {}",
                userId, accountNumber, accountType, currency);

        return saved;
    }

    private String generateAccountNumber() {
        String bban = "000610" + String.format("%016d",
                Math.abs(UUID.randomUUID().getMostSignificantBits() % 10000000000000000L));
        return IbanUtils.createTurkishIban(bban);
    }

    private String getDefaultAccountName(AccountType type, Currency currency) {
        String typeName = switch (type) {
            case CHECKING -> "Vadesiz";
            case SAVINGS -> "Birikim";
            case INVESTMENT -> "Yatırım";
        };
        return typeName + " " + currency.name() + " Hesabı";
    }

    private BigDecimal getDefaultDailyLimit(AccountType type) {
        return switch (type) {
            case CHECKING -> new BigDecimal("50000.00");
            case SAVINGS -> new BigDecimal("25000.00");
            case INVESTMENT -> new BigDecimal("100000.00");
        };
    }

    /**
     * Pipeline'dan gelen işleme göre bakiye güncelle.
     * Pessimistic lock ile eşzamanlı güncelleme önlenir.
     */
    @Transactional
    public void processBalanceUpdate(TransactionEvent event) {
        validateEvent(event);
        TransactionType type = event.getType();

        switch (type) {
            case TRANSFER -> processTransfer(event);
            case DEPOSIT -> processDeposit(event);
            case WITHDRAWAL -> processWithdrawal(event);
            case PAYMENT -> processPayment(event);
        }
    }

    /** Harici EFT/FAST transferi için parayı harcanabilir bakiyeden ayırır. */
    public void reserveExternalTransfer(TransactionEvent event) {
        validateEvent(event);
        if (!isExternalTransfer(event)) {
            throw new BusinessException("Yalnızca EFT/FAST transferleri rezerve edilebilir",
                    "EXTERNAL_TRANSFER_REQUIRED");
        }

        UUID transactionId = UUID.fromString(event.getTransactionId());
        if (fundReservationRepository.existsById(transactionId)) {
            return;
        }

        Account source = accountRepository.findByIdWithLock(event.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getSourceAccountId()));
        validateEventOwnership(source, event);
        validateAccount(source);
        validateCurrency(source, event);
        validateBalance(source, event.getAmount());
        applyDailyLimit(source, event.getAmount());

        source.setReservedBalance(nonNull(source.getReservedBalance()).add(event.getAmount()));
        accountRepository.save(source);
        fundReservationRepository.save(FundReservation.builder()
                .transactionId(transactionId)
                .accountId(source.getId())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .status(FundReservationStatus.RESERVED)
                .dailySpentDate(source.getDailySpentDate())
                .build());
    }

    /** Payment Rail sonucunu kesin bakiyeye ve ledger'a uygular veya rezervasyonu iade eder. */
    public void completeExternalTransfer(TransactionEvent event) {
        validateEvent(event);
        UUID transactionId = UUID.fromString(event.getTransactionId());
        FundReservation reservation = fundReservationRepository.findByTransactionIdWithLock(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Fon rezervasyonu", "transactionId", transactionId));
        if (reservation.getStatus() != FundReservationStatus.RESERVED) {
            return;
        }

        Account source = accountRepository.findByIdWithLock(reservation.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", reservation.getAccountId()));
        if (!Objects.equals(source.getId(), event.getSourceAccountId())
                || reservation.getAmount().compareTo(event.getAmount()) != 0
                || reservation.getCurrency() != event.getCurrency()) {
            throw new BusinessException("Rail sonucu rezervasyon ile eşleşmiyor",
                    "RESERVATION_RESULT_MISMATCH");
        }

        BigDecimal reserved = nonNull(source.getReservedBalance());
        if (reserved.compareTo(event.getAmount()) < 0) {
            throw new BusinessException("Rezerve bakiye tutarsız", "RESERVED_BALANCE_MISMATCH");
        }

        source.setReservedBalance(reserved.subtract(event.getAmount()));
        if (event.getStatus() == com.fintech.common.enums.TransactionStatus.FAILED) {
            releaseDailyLimit(source, reservation);
            reservation.setStatus(FundReservationStatus.RELEASED);
        } else {
            validateBalanceForSettlement(source, event.getAmount());
            source.setBalance(source.getBalance().subtract(event.getAmount()));
            reservation.setStatus(FundReservationStatus.SETTLED);
            ledgerService.post(event, source, null);
            event.setStatus(com.fintech.common.enums.TransactionStatus.PROCESSED);
        }

        accountRepository.save(source);
        fundReservationRepository.save(reservation);
    }

    private void processTransfer(TransactionEvent event) {
        Map<Long, Account> accounts = accountRepository.findAllByIdWithLock(
                        List.of(event.getSourceAccountId(), event.getTargetAccountId()))
                .stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));
        Account source = requireAccount(accounts, event.getSourceAccountId());
        Account target = requireAccount(accounts, event.getTargetAccountId());

        validateEventOwnership(source, event);
        validateAccount(source);
        validateAccount(target);
        validateCurrency(source, event);
        validateCurrency(target, event);
        validateBalance(source, event.getAmount());
        applyDailyLimit(source, event.getAmount());

        source.setBalance(source.getBalance().subtract(event.getAmount()));
        target.setBalance(target.getBalance().add(event.getAmount()));

        accountRepository.save(source);
        accountRepository.save(target);
        ledgerService.post(event, source, target);

        log.info("Transfer tamamlandı - kaynak: {} ({} → {}), hedef: {} ({} → {})",
                source.getAccountNumber(),
                source.getBalance().add(event.getAmount()), source.getBalance(),
                target.getAccountNumber(),
                target.getBalance().subtract(event.getAmount()), target.getBalance());
    }

    private void processDeposit(TransactionEvent event) {
        Account account = accountRepository.findByIdWithLock(event.getTargetAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getTargetAccountId()));
        validateEventOwnership(account, event);
        validateAccount(account);
        validateCurrency(account, event);
        account.setBalance(account.getBalance().add(event.getAmount()));
        accountRepository.save(account);
        ledgerService.post(event, null, account);
        log.info("Para yatırma tamamlandı - hesap: {}, yeni bakiye: {}",
                account.getAccountNumber(), account.getBalance());
    }

    private void processWithdrawal(TransactionEvent event) {
        Account account = accountRepository.findByIdWithLock(event.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getSourceAccountId()));
        validateEventOwnership(account, event);
        validateAccount(account);
        validateCurrency(account, event);
        validateBalance(account, event.getAmount());
        applyDailyLimit(account, event.getAmount());
        account.setBalance(account.getBalance().subtract(event.getAmount()));
        accountRepository.save(account);
        ledgerService.post(event, account, null);
        log.info("Para çekme tamamlandı - hesap: {}, yeni bakiye: {}",
                account.getAccountNumber(), account.getBalance());
    }

    private void processPayment(TransactionEvent event) {
        Account account = accountRepository.findByIdWithLock(event.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getSourceAccountId()));
        validateEventOwnership(account, event);
        validateAccount(account);
        validateCurrency(account, event);
        validateBalance(account, event.getAmount());
        applyDailyLimit(account, event.getAmount());
        account.setBalance(account.getBalance().subtract(event.getAmount()));
        accountRepository.save(account);
        ledgerService.post(event, account, null);
        log.info("Ödeme tamamlandı - hesap: {}, yeni bakiye: {}",
                account.getAccountNumber(), account.getBalance());
    }

    private void validateAccount(Account account) {
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException(account.getId());
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new RuntimeException("Hesap kapalı: " + account.getId());
        }
    }

    private void validateBalance(Account account, BigDecimal amount) {
        if (account.getAvailableBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(account.getId());
        }
    }

    private void validateBalanceForSettlement(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BusinessException("Settlement için bakiye yetersiz", "SETTLEMENT_BALANCE_MISMATCH");
        }
    }

    private void applyDailyLimit(Account account, BigDecimal amount) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        BigDecimal spent = account.getDailySpent();
        if (!today.equals(account.getDailySpentDate())) {
            spent = BigDecimal.ZERO;
            account.setDailySpentDate(today);
        }
        if (spent == null) {
            spent = BigDecimal.ZERO;
        }

        BigDecimal updated = spent.add(amount);
        if (updated.compareTo(account.getDailyLimit()) > 0) {
            throw new BusinessException(
                    "Günlük işlem limiti aşıldı. Limit: " + account.getDailyLimit() + " " + account.getCurrency(),
                    "DAILY_LIMIT_EXCEEDED",
                    org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY);
        }
        account.setDailySpent(updated);
    }

    private void releaseDailyLimit(Account account, FundReservation reservation) {
        if (reservation.getDailySpentDate().equals(account.getDailySpentDate())) {
            account.setDailySpent(nonNull(account.getDailySpent())
                    .subtract(reservation.getAmount()).max(BigDecimal.ZERO));
        }
    }

    private BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateEvent(TransactionEvent event) {
        if (event == null || event.getTransactionId() == null || event.getTransactionId().isBlank()
                || event.getType() == null || event.getCurrency() == null || event.getAmount() == null
                || event.getAmount().compareTo(BigDecimal.ZERO) <= 0 || event.getAmount().scale() > 2) {
            throw new BusinessException("Geçersiz finansal işlem olayı", "INVALID_TRANSACTION_EVENT");
        }

        switch (event.getType()) {
            case TRANSFER -> {
                if (isExternalTransfer(event)) {
                    if (event.getSourceAccountId() == null || event.getTargetAccountId() != null
                            || event.getBeneficiaryIban() == null || event.getBeneficiaryIban().isBlank()) {
                        throw new BusinessException("EFT/FAST kaynak hesap ve alıcı IBAN gerektirir",
                                "INVALID_EXTERNAL_TRANSFER");
                    }
                } else if (event.getSourceAccountId() == null || event.getTargetAccountId() == null
                        || Objects.equals(event.getSourceAccountId(), event.getTargetAccountId())) {
                    throw new BusinessException("Transfer farklı kaynak ve hedef hesap gerektirir",
                            "INVALID_TRANSFER_ACCOUNTS");
                }
            }
            case PAYMENT, WITHDRAWAL -> {
                if (event.getSourceAccountId() == null || event.getTargetAccountId() != null) {
                    throw new BusinessException("Bu işlem yalnızca kaynak hesap gerektirir", "INVALID_TRANSACTION_ACCOUNTS");
                }
            }
            case DEPOSIT -> {
                if (event.getSourceAccountId() != null || event.getTargetAccountId() == null) {
                    throw new BusinessException("Para yatırma yalnızca hedef hesap gerektirir", "INVALID_DEPOSIT_ACCOUNTS");
                }
                if (!"ADMIN".equals(event.getInitiatorRole())) {
                    throw new ForbiddenException();
                }
            }
        }
    }

    private void validateCurrency(Account account, TransactionEvent event) {
        if (account.getCurrency() != event.getCurrency()) {
            throw new BusinessException(
                    "Hesap para birimi işlem para birimiyle eşleşmiyor",
                    "CURRENCY_MISMATCH");
        }
    }

    private Account requireAccount(Map<Long, Account> accounts, Long id) {
        Account account = accounts.get(id);
        if (account == null) {
            throw new ResourceNotFoundException("Hesap", "id", id);
        }
        return account;
    }

    private void validateEventOwnership(Account account, TransactionEvent event) {
        if (event.getType() == TransactionType.DEPOSIT && "ADMIN".equals(event.getInitiatorRole())) {
            return;
        }
        if (event.getUserId() == null || !Objects.equals(account.getUserId(), event.getUserId())) {
            throw new ForbiddenException();
        }
    }

    // ── REST API için CRUD metodları ──

    public Account getAccountById(Long id, Long authenticatedUserId, boolean administrator) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", id));
        validateApiOwnership(account, authenticatedUserId, administrator);
        return account;
    }

    public List<Account> getAccountsByUserId(Long userId, Long authenticatedUserId, boolean administrator) {
        if (!administrator && !Objects.equals(userId, authenticatedUserId)) {
            throw new ForbiddenException();
        }
        return accountRepository.findByUserId(userId);
    }

    public Account getAccountByNumber(String accountNumber, Long authenticatedUserId, boolean administrator) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "accountNumber", accountNumber));
        validateApiOwnership(account, authenticatedUserId, administrator);
        return account;
    }

    private void validateApiOwnership(Account account, Long authenticatedUserId, boolean administrator) {
        if (!administrator && !Objects.equals(account.getUserId(), authenticatedUserId)) {
            throw new ForbiddenException();
        }
    }

    public InternalBeneficiaryResponse resolveInternalBeneficiary(String iban) {
        String normalized = IbanUtils.normalize(iban);
        Account account = accountRepository.findByAccountNumber(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Alıcı hesabı", "iban", normalized));
        UserSnapshot owner = userDirectoryClient.getUser(account.getUserId());
        return InternalBeneficiaryResponse.builder()
                .accountId(account.getId())
                .userId(account.getUserId())
                .iban(account.getAccountNumber())
                .currency(account.getCurrency().name())
                .status(account.getStatus().name())
                .beneficiaryName(owner.getDisplayName())
                .build();
    }

    public AccountSnapshot getInternalAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", accountId));
        return toSnapshot(account);
    }

    public AccountSnapshot getInternalAccountByIban(String iban) {
        String normalized = IbanUtils.normalize(iban);
        Account account = accountRepository.findByAccountNumber(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "iban", normalized));
        return toSnapshot(account);
    }

    public List<Long> getInternalAccountIdsByUser(Long userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(Account::getId)
                .toList();
    }

    private AccountSnapshot toSnapshot(Account account) {
        return AccountSnapshot.builder()
                .accountId(account.getId())
                .userId(account.getUserId())
                .accountNumber(account.getAccountNumber())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .build();
    }

    private boolean isExternalTransfer(TransactionEvent event) {
        return event.getType() == TransactionType.TRANSFER
                && (event.getTransferRail() == TransferRail.EFT || event.getTransferRail() == TransferRail.FAST);
    }
}
