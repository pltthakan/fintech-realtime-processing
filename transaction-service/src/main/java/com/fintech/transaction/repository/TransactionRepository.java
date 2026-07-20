package com.fintech.transaction.repository;

import com.fintech.common.enums.TransactionStatus;
import com.fintech.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdWithLock(@Param("id") UUID id);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.sourceAccountId = :accountId OR t.targetAccountId = :accountId
            ORDER BY t.createdAt DESC
            """)
    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(
            @Param("accountId") Long accountId, Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.sourceAccountId IN :accountIds OR t.targetAccountId IN :accountIds
            ORDER BY t.createdAt DESC
            """)
    Page<Transaction> findByParticipantAccountIds(
            @Param("accountIds") Collection<Long> accountIds, Pageable pageable);

    Page<Transaction> findByStatusOrderByCreatedAtDesc(TransactionStatus status, Pageable pageable);

    long countBySourceAccountIdAndStatus(Long sourceAccountId, TransactionStatus status);

}
