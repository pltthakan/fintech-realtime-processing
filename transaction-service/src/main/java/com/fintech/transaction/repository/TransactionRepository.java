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
import java.util.List;
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

    @Query(value = """
            SELECT t.*
            FROM transaction_service.transactions t
            WHERE t.source_account_id IN (
                SELECT a.id FROM account_service.accounts a WHERE a.user_id = :userId
            ) OR t.target_account_id IN (
                SELECT a.id FROM account_service.accounts a WHERE a.user_id = :userId
            )
            ORDER BY t.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM transaction_service.transactions t
            WHERE t.source_account_id IN (
                SELECT a.id FROM account_service.accounts a WHERE a.user_id = :userId
            ) OR t.target_account_id IN (
                SELECT a.id FROM account_service.accounts a WHERE a.user_id = :userId
            )
            """,
            nativeQuery = true)
    Page<Transaction> findByParticipantUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(value = """
            SELECT id FROM account_service.accounts WHERE user_id = :userId
            """, nativeQuery = true)
    List<Long> findAccountIdsOwnedBy(@Param("userId") Long userId);

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

    @Query(value = """
            SELECT id,
                   user_id AS userId,
                   account_number AS accountNumber,
                   currency,
                   status
            FROM account_service.accounts
            WHERE account_number = :accountNumber
            """, nativeQuery = true)
    Optional<AccountRoutingView> findAccountForRouting(@Param("accountNumber") String accountNumber);

    @Query(value = """
            SELECT id,
                   user_id AS userId,
                   account_number AS accountNumber,
                   currency,
                   status
            FROM account_service.accounts
            WHERE id = :accountId
            """, nativeQuery = true)
    Optional<AccountRoutingView> findAccountForRoutingById(@Param("accountId") Long accountId);
}
