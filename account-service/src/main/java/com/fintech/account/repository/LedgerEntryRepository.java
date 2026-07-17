package com.fintech.account.repository;

import com.fintech.account.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    List<LedgerEntry> findByLedgerTransactionIdOrderByCreatedAtAsc(UUID ledgerTransactionId);

    Optional<LedgerEntry> findFirstByAccountIdOrderByCreatedAtDesc(Long accountId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM account_service.ledger_entries le
                JOIN account_service.accounts a ON a.id = le.account_id
                WHERE le.ledger_transaction_id = :transactionId
                  AND a.user_id = :userId
            )
            """, nativeQuery = true)
    boolean isVisibleToUser(@Param("transactionId") UUID transactionId, @Param("userId") Long userId);
}
