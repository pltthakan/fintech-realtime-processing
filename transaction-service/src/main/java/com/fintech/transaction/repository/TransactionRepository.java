package com.fintech.transaction.repository;

import com.fintech.common.enums.TransactionStatus;
import com.fintech.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Page<Transaction> findBySourceAccountIdOrderByCreatedAtDesc(Long sourceAccountId, Pageable pageable);

    Page<Transaction> findBySourceAccountIdAndUserIdOrderByCreatedAtDesc(
            Long sourceAccountId, Long userId, Pageable pageable);

    Page<Transaction> findByTargetAccountIdOrderByCreatedAtDesc(Long targetAccountId, Pageable pageable);

    Page<Transaction> findByStatusOrderByCreatedAtDesc(TransactionStatus status, Pageable pageable);

    long countBySourceAccountIdAndStatus(Long sourceAccountId, TransactionStatus status);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM account_service.accounts
                WHERE id = :accountId AND user_id = :userId
            )
            """, nativeQuery = true)
    boolean existsAccountOwnedBy(@Param("accountId") Long accountId, @Param("userId") Long userId);
}
