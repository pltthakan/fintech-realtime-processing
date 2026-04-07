package com.fintech.account.service;

import com.fintech.account.entity.Account;
import com.fintech.account.repository.AccountRepository;
import com.fintech.common.enums.AccountStatus;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.exception.AccountFrozenException;
import com.fintech.common.exception.InsufficientBalanceException;
import com.fintech.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

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

    /**
     * Transfer: kaynak hesaptan düş, hedef hesaba ekle
     */
    private void processTransfer(TransactionEvent event) {
        // Kaynak hesap (pessimistic lock)
        Account source = accountRepository.findByIdWithLock(event.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getSourceAccountId()));

        validateAccount(source);
        validateBalance(source, event.getAmount());

        // Hedef hesap (pessimistic lock)
        Account target = accountRepository.findByIdWithLock(event.getTargetAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getTargetAccountId()));

        validateAccount(target);

        // Bakiye güncelle
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

    /**
     * Para yatırma: hedef hesaba ekle
     */
    private void processDeposit(TransactionEvent event) {
        Account account = accountRepository.findByIdWithLock(event.getTargetAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getTargetAccountId()));

        validateAccount(account);

        account.setBalance(account.getBalance().add(event.getAmount()));
        accountRepository.save(account);

        log.info("Para yatırma tamamlandı - hesap: {}, yeni bakiye: {}",
                account.getAccountNumber(), account.getBalance());
    }

    /**
     * Para çekme: kaynak hesaptan düş
     */
    private void processWithdrawal(TransactionEvent event) {
        Account account = accountRepository.findByIdWithLock(event.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getSourceAccountId()));

        validateAccount(account);
        validateBalance(account, event.getAmount());

        account.setBalance(account.getBalance().subtract(event.getAmount()));
        accountRepository.save(account);

        log.info("Para çekme tamamlandı - hesap: {}, yeni bakiye: {}",
                account.getAccountNumber(), account.getBalance());
    }

    /**
     * Ödeme: kaynak hesaptan düş
     */
    private void processPayment(TransactionEvent event) {
        Account account = accountRepository.findByIdWithLock(event.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", event.getSourceAccountId()));

        validateAccount(account);
        validateBalance(account, event.getAmount());

        account.setBalance(account.getBalance().subtract(event.getAmount()));
        accountRepository.save(account);

        log.info("Ödeme tamamlandı - hesap: {}, yeni bakiye: {}",
                account.getAccountNumber(), account.getBalance());
    }

    /** Hesap aktif mi kontrolü */
    private void validateAccount(Account account) {
        if (account.getStatus() == AccountStatus.FROZEN) {
            throw new AccountFrozenException(account.getId());
        }
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new RuntimeException("Hesap kapalı: " + account.getId());
        }
    }

    /** Yeterli bakiye var mı kontrolü */
    private void validateBalance(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(account.getId());
        }
    }

    // ── REST API için CRUD metodları ──

    public Account getAccountById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", id));
    }

    public List<Account> getAccountsByUserId(Long userId) {
        return accountRepository.findByUserId(userId);
    }

    public Account getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "accountNumber", accountNumber));
    }
}
