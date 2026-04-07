package com.fintech.fraud.repository;

import com.fintech.fraud.entity.FraudCheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FraudCheckResultRepository extends JpaRepository<FraudCheckResult, Long> {

    Optional<FraudCheckResult> findByTransactionId(UUID transactionId);
}
