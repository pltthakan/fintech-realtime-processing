package com.fintech.notification.service;

import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.NotificationEvent;
import com.fintech.common.event.RabbitConstants;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Pipeline'ın D (son) adımı.
 * transaction-checked'den okur → bildirim gönderir (RabbitMQ)
 * → transaction-processed'e yazar → transaction-completed'e yazar (→ Kafka Connect → MongoDB)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPipelineService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RabbitTemplate rabbitTemplate;

    @KafkaListener(
            topics = KafkaTopics.TRANSACTION_CHECKED,
            groupId = "notification-service-group",
            concurrency = "3"
    )
    public void processTransaction(String message) {
        try {
            TransactionEvent event = JsonUtil.fromJson(message, TransactionEvent.class);
            boolean failed = event.getStatus() == TransactionStatus.FAILED;
            log.info("Bildirim işlemi başlıyor - txId: {}, status: {}, amount: {} {}",
                    event.getTransactionId(), event.getStatus(), event.getAmount(), event.getCurrency());

            // 1. RabbitMQ'ya bildirim gönder (Email + SMS + Push)
            sendNotifications(event);

            // 2. Event'i güncelle
            if (!failed) {
                event.setStatus(TransactionStatus.COMPLETED);
            }
            event.setProcessedTimestamp(Instant.now());
            event.setCompletedTimestamp(Instant.now());

            // Toplam işleme süresini hesapla
            if (event.getRawTimestamp() != null) {
                long totalMs = Duration.between(event.getRawTimestamp(), Instant.now()).toMillis();
                event.setTotalProcessingTimeMs(totalMs);
            }

            String eventJson = JsonUtil.toJson(event);

            // 3. transaction-processed topic'ine yaz
            if (!failed) {
                kafkaTemplate.send(KafkaTopics.TRANSACTION_PROCESSED, event.getTransactionId(), eventJson).join();
            }

            // 4. transaction-completed topic'ine yaz (Kafka Connect → MongoDB)
            var result = kafkaTemplate.send(
                    KafkaTopics.TRANSACTION_COMPLETED, event.getTransactionId(), eventJson).join();
            log.info("Pipeline TAMAMLANDI - txId: {}, toplam süre: {}ms, topic: {}",
                    event.getTransactionId(), event.getTotalProcessingTimeMs(),
                    result.getRecordMetadata().topic());

        } catch (Exception e) {
            log.error("Notification pipeline hatası: {}", e.getMessage(), e);
            throw new IllegalStateException("Notification pipeline tamamlanamadı", e);
        }
    }

    /**
     * RabbitMQ üzerinden Email, SMS, Push bildirimleri gönder
     */
    private void sendNotifications(TransactionEvent event) {
        boolean failed = event.getStatus() == TransactionStatus.FAILED;
        NotificationEvent.NotificationType notificationType = failed
                ? NotificationEvent.NotificationType.TRANSACTION_FAILED
                : NotificationEvent.NotificationType.TRANSACTION_COMPLETED;
        String subject = (failed ? "İşlem Başarısız - " : "İşlem Tamamlandı - ")
                + event.getReferenceNumber();
        String message = failed
                ? String.format("%s işleminiz tamamlanamadı. Tutar: %s %s, Referans: %s, Neden: %s",
                        event.getType(), event.getAmount(), event.getCurrency(), event.getReferenceNumber(),
                        event.getRailFailureReason() == null ? "Ödeme ağı reddi" : event.getRailFailureReason())
                : String.format("%s işleminiz tamamlandı. Tutar: %s %s, Referans: %s",
                        event.getType(), event.getAmount(), event.getCurrency(), event.getReferenceNumber());
        NotificationEvent notification = NotificationEvent.builder()
                .notificationId(UUID.randomUUID().toString())
                .userId(event.getUserId())
                .transactionId(event.getTransactionId())
                .type(notificationType)
                .subject(subject)
                .message(message)
                .templateData(Map.of(
                        "transactionId", event.getTransactionId(),
                        "amount", event.getAmount().toString(),
                        "currency", event.getCurrency().name(),
                        "type", event.getType().name(),
                        "referenceNumber", event.getReferenceNumber()
                ))
                .createdAt(Instant.now())
                .build();

        // Email bildirimi
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConstants.NOTIFICATION_EXCHANGE,
                    RabbitConstants.EMAIL_ROUTING_KEY,
                    notification
            );
            log.info("Email bildirimi RabbitMQ'ya gönderildi - txId: {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Email bildirimi gönderilemedi: {}", e.getMessage());
        }

        // SMS bildirimi
        try {
            notification.setType(notificationType);
            rabbitTemplate.convertAndSend(
                    RabbitConstants.NOTIFICATION_EXCHANGE,
                    RabbitConstants.SMS_ROUTING_KEY,
                    notification
            );
            log.info("SMS bildirimi RabbitMQ'ya gönderildi - txId: {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("SMS bildirimi gönderilemedi: {}", e.getMessage());
        }

        // Push bildirimi
        try {
            notification.setType(notificationType);
            rabbitTemplate.convertAndSend(
                    RabbitConstants.NOTIFICATION_EXCHANGE,
                    RabbitConstants.PUSH_ROUTING_KEY,
                    notification
            );
            log.info("Push bildirimi RabbitMQ'ya gönderildi - txId: {}", event.getTransactionId());
        } catch (Exception e) {
            log.error("Push bildirimi gönderilemedi: {}", e.getMessage());
        }
    }
}
