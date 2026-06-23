package com.fintech.reporting.repository;

import com.fintech.reporting.dto.CompletedTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompletedTransactionRepository extends MongoRepository<CompletedTransaction, String> {

    Optional<CompletedTransaction> findByTransactionId(String transactionId);

    Page<CompletedTransaction> findByUserIdOrderByCompletedTimestampDesc(Long userId, Pageable pageable);

    Page<CompletedTransaction> findBySourceAccountIdOrTargetAccountIdOrderByCompletedTimestampDesc(
            Long sourceAccountId, Long targetAccountId, Pageable pageable);

    @Query("{ 'completedTimestamp': { '$gte': ?0, '$lt': ?1 } }")
    List<CompletedTransaction> findByCompletedTimestampRange(String start, String end);

    List<CompletedTransaction> findByIsSuspiciousTrue();

    List<CompletedTransaction> findByIsBlockedTrue();

    long countByStatus(String status);

    long countByIsSuspiciousTrue();

    long countByIsBlockedTrue();

    @Query("{ 'type': ?0, 'completedTimestamp': { '$gte': ?1, '$lt': ?2 } }")
    List<CompletedTransaction> findByTypeAndCompletedTimestampRange(String type, String start, String end);
}
