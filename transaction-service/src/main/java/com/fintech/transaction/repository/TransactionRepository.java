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
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.id = :id")
    Optional<Transaction> findByIdWithLock(@Param("id") UUID id);

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

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM account_service.accounts
                WHERE id = :accountId
            )
            """, nativeQuery = true)
    boolean existsAccount(@Param("accountId") Long accountId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM account_service.accounts
                WHERE id = :accountId AND currency = :currency AND status = 'ACTIVE'
            )
            """, nativeQuery = true)
    boolean existsActiveAccountWithCurrency(
            @Param("accountId") Long accountId,
            @Param("currency") String currency);
}
