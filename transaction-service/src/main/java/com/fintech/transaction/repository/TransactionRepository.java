package com.fintech.transaction.repository;

import com.fintech.common.enums.TransactionStatus;
import com.fintech.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Page<Transaction> findBySourceAccountIdOrderByCreatedAtDesc(Long sourceAccountId, Pageable pageable);

    Page<Transaction> findByTargetAccountIdOrderByCreatedAtDesc(Long targetAccountId, Pageable pageable);

    Page<Transaction> findByStatusOrderByCreatedAtDesc(TransactionStatus status, Pageable pageable);

    long countBySourceAccountIdAndStatus(Long sourceAccountId, TransactionStatus status);
}
