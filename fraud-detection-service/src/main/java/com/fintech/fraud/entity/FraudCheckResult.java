package com.fintech.fraud.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "fraud_check_results", schema = "fraud_service")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private UUID transactionId;

    @Column(name = "total_risk_score", nullable = false)
    private Short totalRiskScore;

    @Column(name = "is_suspicious", nullable = false)
    @Builder.Default
    private Boolean isSuspicious = false;

    @Column(name = "is_blocked", nullable = false)
    @Builder.Default
    private Boolean isBlocked = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "matched_rules", columnDefinition = "jsonb")
    private Map<String, Object> matchedRules;

    @Column(name = "check_duration_ms")
    private Integer checkDurationMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
