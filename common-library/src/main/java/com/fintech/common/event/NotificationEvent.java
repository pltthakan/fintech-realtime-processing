package com.fintech.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * RabbitMQ üzerinden Notification Service'e gönderilen event.
 * Fraud engeli, işlem tamamlanma, bakiye uyarısı gibi bildirimler için kullanılır.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    private String notificationId;
    private Long userId;
    private String email;
    private String phoneNumber;
    private NotificationType type;
    private String subject;
    private String message;
    private String transactionId;
    private Map<String, Object> templateData;
    private Instant createdAt;

    public enum NotificationType {
        TRANSACTION_COMPLETED,
        TRANSACTION_FAILED,
        FRAUD_ALERT,
        LOW_BALANCE_WARNING,
        ACCOUNT_FROZEN,
        LOGIN_ALERT
    }
}
