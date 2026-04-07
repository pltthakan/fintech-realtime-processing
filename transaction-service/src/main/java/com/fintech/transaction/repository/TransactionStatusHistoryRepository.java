package com.fintech.transaction.repository;

import com.fintech.transaction.entity.TransactionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionStatusHistoryRepository extends JpaRepository<TransactionStatusHistory, Long> {

    List<TransactionStatusHistory> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}
