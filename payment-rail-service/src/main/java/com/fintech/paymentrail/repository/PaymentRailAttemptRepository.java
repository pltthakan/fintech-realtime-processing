package com.fintech.paymentrail.repository;

import com.fintech.paymentrail.entity.PaymentRailAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRailAttemptRepository extends JpaRepository<PaymentRailAttempt, UUID> {
    Optional<PaymentRailAttempt> findByTransactionId(UUID transactionId);
}
