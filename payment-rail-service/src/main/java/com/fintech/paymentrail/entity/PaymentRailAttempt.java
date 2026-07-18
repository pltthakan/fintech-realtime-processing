package com.fintech.paymentrail.entity;

import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransferRail;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_rail_attempts", schema = "payment_rail_service")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRailAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private UUID transactionId;

    @Column(name = "external_reference", nullable = false, unique = true, length = 80)
    private String externalReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferRail rail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentRailStatus status;

    @Column(name = "beneficiary_iban_hash", nullable = false, length = 64)
    private String beneficiaryIbanHash;

    @Column(name = "beneficiary_iban_masked", nullable = false, length = 40)
    private String beneficiaryIbanMasked;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
