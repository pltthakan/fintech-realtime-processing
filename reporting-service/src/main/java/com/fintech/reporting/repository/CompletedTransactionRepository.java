package com.fintech.reporting.repository;

import com.fintech.reporting.dto.CompletedTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CompletedTransactionRepository extends MongoRepository<CompletedTransaction, String> {

    Optional<CompletedTransaction> findByTransactionId(String transactionId);

    Page<CompletedTransaction> findByUserIdOrderByCompletedTimestampDesc(Long userId, Pageable pageable);

    Page<CompletedTransaction> findBySourceAccountIdOrderByCompletedTimestampDesc(Long accountId, Pageable pageable);

    List<CompletedTransaction> findByCompletedTimestampBetween(Instant start, Instant end);

    List<CompletedTransaction> findByIsSuspiciousTrue();

    List<CompletedTransaction> findByIsBlockedTrue();

    long countByStatus(String status);

    long countByIsSuspiciousTrue();

    long countByIsBlockedTrue();

    List<CompletedTransaction> findByTypeAndCompletedTimestampBetween(String type, Instant start, Instant end);
}
