package com.fintech.account.service;

import com.fintech.account.entity.Account;
import com.fintech.account.repository.AccountRepository;
import com.fintech.common.enums.AccountStatus;
import com.fintech.common.enums.AccountType;
import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.exception.AccountFrozenException;
import com.fintech.common.exception.ForbiddenException;
import com.fintech.common.exception.InsufficientBalanceException;
import com.fintech.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    /**
     * Yeni hesap oluştur.
     */
    @Transactional
    public Account createAccount(Long userId, String accountName, AccountType accountType,
                                 Currency currency, BigDecimal initialBalance) {
        // IBAN benzeri hesap numarası üret
        String accountNumber = generateAccountNumber();

        Account account = Account.builder()
                .userId(userId)
                .accountNumber(accountNumber)
                .accountName(accountName != null ? accountName : getDefaultAccountName(accountType, currency))
                .accountType(accountType)
                .currency(currency)
                .balance(initialBalance != null ? initialBalance : BigDecimal.ZERO)
                .dailyLimit(getDefaultDailyLimit(accountType))
                .status(AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(account);
        log.info("Yeni hesap oluşturuldu - userId: {}, accountNumber: {}, type: {}, currency: {}",
                userId, accountNumber, accountType, currency);

        return saved;
    }

    private String generateAccountNumber() {
        String base = "TR33" + "0006" + "10" + String.format("%016d",
                Math.abs(UUID.randomUUID().getMostSignificantBits() % 10000000000000000L));
        return base;
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
        TransactionType type = event.getType();

        switch (type) {
            case TRANSFER -> processTransfer(event);
            case DEPOSIT -> processDeposit(event);
            case WITHDRAWAL -> processWithdrawal(event);
            case PAYMENT -> processPayment(event);
        }
    }

    private void processTransfer(TransactionEvent event) {
        Account source = accountRepository.findByIdWithLock(event.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getSourceAccountId()));
        validateEventOwnership(source, event);
        validateAccount(source);
        validateBalance(source, event.getAmount());

        Account target = accountRepository.findByIdWithLock(event.getTargetAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getTargetAccountId()));
        validateAccount(target);

        source.setBalance(source.getBalance().subtract(event.getAmount()));
        target.setBalance(target.getBalance().add(event.getAmount()));

        accountRepository.save(source);
        accountRepository.save(target);

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
        account.setBalance(account.getBalance().add(event.getAmount()));
        accountRepository.save(account);
        log.info("Para yatırma tamamlandı - hesap: {}, yeni bakiye: {}",
                account.getAccountNumber(), account.getBalance());
    }

    private void processWithdrawal(TransactionEvent event) {
        Account account = accountRepository.findByIdWithLock(event.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getSourceAccountId()));
        validateEventOwnership(account, event);
        validateAccount(account);
        validateBalance(account, event.getAmount());
        account.setBalance(account.getBalance().subtract(event.getAmount()));
        accountRepository.save(account);
        log.info("Para çekme tamamlandı - hesap: {}, yeni bakiye: {}",
                account.getAccountNumber(), account.getBalance());
    }

    private void processPayment(TransactionEvent event) {
        Account account = accountRepository.findByIdWithLock(event.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getSourceAccountId()));
        validateEventOwnership(account, event);
        validateAccount(account);
        validateBalance(account, event.getAmount());
        account.setBalance(account.getBalance().subtract(event.getAmount()));
        accountRepository.save(account);
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
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(account.getId());
        }
    }

    private void validateEventOwnership(Account account, TransactionEvent event) {
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
}
