package com.fintech.account.service;

import com.fintech.account.dto.LedgerEntryResponse;
import com.fintech.account.dto.LedgerTransactionResponse;
import com.fintech.account.dto.AccountReconciliationResponse;
import com.fintech.account.entity.Account;
import com.fintech.account.entity.LedgerDirection;
import com.fintech.account.entity.LedgerEntry;
import com.fintech.account.entity.LedgerTransaction;
import com.fintech.account.repository.LedgerEntryRepository;
import com.fintech.account.repository.LedgerTransactionRepository;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.exception.BusinessException;
import com.fintech.common.exception.ForbiddenException;
import com.fintech.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private static final String CASH_CLEARING = "SYSTEM:CASH_CLEARING";
    private static final String PAYMENT_CLEARING = "SYSTEM:PAYMENT_CLEARING";
    private static final String OUTBOUND_CLEARING = "SYSTEM:OUTBOUND_CLEARING";

    private final LedgerTransactionRepository transactionRepository;
    private final LedgerEntryRepository entryRepository;

    /**
     * Bakiye değişikliğiyle aynı veritabanı transaction'ında dengeli journal oluşturur.
     * MANDATORY, bu metodun yanlışlıkla tek başına çağrılmasını engeller.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void post(TransactionEvent event, Account source, Account target) {
        UUID transactionId = UUID.fromString(event.getTransactionId());
        if (transactionRepository.existsById(transactionId)) {
            return;
        }

        Instant postedAt = Instant.now();
        List<LedgerEntry> entries = buildEntries(event, source, target, transactionId, postedAt);
        assertBalanced(entries);

        transactionRepository.save(LedgerTransaction.builder()
                .id(transactionId)
                .referenceNumber(event.getReferenceNumber() == null || event.getReferenceNumber().isBlank()
                        ? transactionId.toString() : event.getReferenceNumber())
                .transactionType(event.getType())
                .currency(event.getCurrency())
                .totalAmount(event.getAmount())
                .status("POSTED")
                .postedAt(postedAt)
                .build());
        entryRepository.saveAll(entries);
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getAccountEntries(Long accountId, Pageable pageable) {
        return entryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(this::toEntryResponse);
    }

    @Transactional(readOnly = true)
    public LedgerTransactionResponse getTransaction(UUID transactionId, Long userId, boolean administrator) {
        LedgerTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger işlemi", "id", transactionId));
        if (!administrator && !entryRepository.isVisibleToUser(transactionId, userId)) {
            throw new ForbiddenException();
        }
        List<LedgerEntry> entries = entryRepository
                .findByLedgerTransactionIdOrderByCreatedAtAsc(transactionId);
        BigDecimal debitTotal = total(entries, LedgerDirection.DEBIT);
        BigDecimal creditTotal = total(entries, LedgerDirection.CREDIT);

        return LedgerTransactionResponse.builder()
                .transactionId(transaction.getId())
                .referenceNumber(transaction.getReferenceNumber())
                .transactionType(transaction.getTransactionType())
                .currency(transaction.getCurrency())
                .totalAmount(transaction.getTotalAmount())
                .debitTotal(debitTotal)
                .creditTotal(creditTotal)
                .balanced(debitTotal.compareTo(creditTotal) == 0)
                .status(transaction.getStatus())
                .postedAt(transaction.getPostedAt())
                .entries(entries.stream().map(this::toEntryResponse).toList())
                .build();
    }

    @Transactional(readOnly = true)
    public AccountReconciliationResponse reconcile(Account account) {
        BigDecimal ledgerBalance = entryRepository.findFirstByAccountIdOrderByCreatedAtDesc(account.getId())
                .map(LedgerEntry::getBalanceAfter)
                .orElse(BigDecimal.ZERO);
        BigDecimal difference = account.getBalance().subtract(ledgerBalance);
        return AccountReconciliationResponse.builder()
                .accountId(account.getId())
                .currency(account.getCurrency())
                .accountBalance(account.getBalance())
                .latestLedgerBalance(ledgerBalance)
                .difference(difference)
                .reconciled(difference.compareTo(BigDecimal.ZERO) == 0)
                .checkedAt(Instant.now())
                .build();
    }

    private List<LedgerEntry> buildEntries(
            TransactionEvent event, Account source, Account target, UUID transactionId, Instant postedAt) {
        return switch (event.getType()) {
            case TRANSFER -> target == null
                    ? List.of(
                            accountEntry(transactionId, source, LedgerDirection.DEBIT, event.getAmount(), postedAt),
                            systemEntry(transactionId, OUTBOUND_CLEARING, LedgerDirection.CREDIT, event, postedAt))
                    : List.of(
                            accountEntry(transactionId, source, LedgerDirection.DEBIT, event.getAmount(), postedAt),
                            accountEntry(transactionId, target, LedgerDirection.CREDIT, event.getAmount(), postedAt));
            case DEPOSIT -> List.of(
                    systemEntry(transactionId, CASH_CLEARING, LedgerDirection.DEBIT, event, postedAt),
                    accountEntry(transactionId, target, LedgerDirection.CREDIT, event.getAmount(), postedAt));
            case WITHDRAWAL -> List.of(
                    accountEntry(transactionId, source, LedgerDirection.DEBIT, event.getAmount(), postedAt),
                    systemEntry(transactionId, CASH_CLEARING, LedgerDirection.CREDIT, event, postedAt));
            case PAYMENT -> List.of(
                    accountEntry(transactionId, source, LedgerDirection.DEBIT, event.getAmount(), postedAt),
                    systemEntry(transactionId, PAYMENT_CLEARING, LedgerDirection.CREDIT, event, postedAt));
        };
    }

    private LedgerEntry accountEntry(
            UUID transactionId, Account account, LedgerDirection direction, BigDecimal amount, Instant createdAt) {
        if (account == null) {
            throw new BusinessException("Ledger hesap kaydı eksik", "LEDGER_ACCOUNT_REQUIRED");
        }
        return LedgerEntry.builder()
                .ledgerTransactionId(transactionId)
                .accountId(account.getId())
                .accountCode("ACCOUNT:" + account.getId())
                .direction(direction)
                .amount(amount)
                .currency(account.getCurrency())
                .balanceAfter(account.getBalance())
                .createdAt(createdAt)
                .build();
    }

    private LedgerEntry systemEntry(
            UUID transactionId, String accountCode, LedgerDirection direction,
            TransactionEvent event, Instant createdAt) {
        return LedgerEntry.builder()
                .ledgerTransactionId(transactionId)
                .accountCode(accountCode)
                .direction(direction)
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .createdAt(createdAt)
                .build();
    }

    private void assertBalanced(List<LedgerEntry> entries) {
        BigDecimal debits = total(entries, LedgerDirection.DEBIT);
        BigDecimal credits = total(entries, LedgerDirection.CREDIT);
        if (entries.size() < 2 || debits.compareTo(credits) != 0) {
            throw new BusinessException("Dengesiz journal kaydı reddedildi", "UNBALANCED_LEDGER");
        }
    }

    private BigDecimal total(List<LedgerEntry> entries, LedgerDirection direction) {
        return entries.stream()
                .filter(entry -> entry.getDirection() == direction)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LedgerEntryResponse toEntryResponse(LedgerEntry entry) {
        return LedgerEntryResponse.builder()
                .id(entry.getId())
                .transactionId(entry.getLedgerTransactionId())
                .accountId(entry.getAccountId())
                .accountCode(entry.getAccountCode())
                .direction(entry.getDirection())
                .amount(entry.getAmount())
                .currency(entry.getCurrency())
                .balanceAfter(entry.getBalanceAfter())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
