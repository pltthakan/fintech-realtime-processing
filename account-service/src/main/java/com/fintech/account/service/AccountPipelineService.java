package com.fintech.account.service;

import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Kafka Pipeline'ın C adımı.
 * transaction-validated'dan okur → bakiye günceller → transaction-checked'e yazar
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPipelineService {

    private final AccountService accountService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(
            topics = KafkaTopics.TRANSACTION_VALIDATED,
            groupId = "account-service-group",
            concurrency = "3"
    )
    public void processTransaction(String message) {
        try {
            TransactionEvent event = JsonUtil.fromJson(message, TransactionEvent.class);

            // Engellenen işlemleri atla
            if (event.getStatus() == TransactionStatus.BLOCKED) {
                log.info("Engellenen işlem atlanıyor - txId: {}, fraudScore: {}",
                        event.getTransactionId(), event.getFraudScore());
                return;
            }

            log.info("Bakiye güncelleme başlıyor - txId: {}, type: {}, amount: {} {}",
                    event.getTransactionId(), event.getType(), event.getAmount(), event.getCurrency());

            // Bakiye güncelle
            accountService.processBalanceUpdate(event);

            // Event'i güncelle
            event.setStatus(TransactionStatus.PROCESSED);
            event.setProcessedTimestamp(Instant.now());

            // transaction-checked topic'ine yaz
            kafkaTemplate.send(KafkaTopics.TRANSACTION_CHECKED, event.getTransactionId(),
                    JsonUtil.toJson(event))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Kafka'ya yazma hatası - txId: {}", event.getTransactionId(), ex);
                        } else {
                            log.info("Bakiye güncellendi, Kafka'ya yazıldı - txId: {}, topic: {}",
                                    event.getTransactionId(), result.getRecordMetadata().topic());
                        }
                    });

        } catch (Exception e) {
            log.error("Account pipeline hatası: {}", e.getMessage(), e);
            // Saga compensating transaction burada yapılabilir
            handleFailure(message, e);
        }
    }

    /**
     * Saga Pattern - Hata durumunda telafi işlemi
     */
    private void handleFailure(String message, Exception e) {
        try {
            TransactionEvent event = JsonUtil.fromJson(message, TransactionEvent.class);
            event.setStatus(TransactionStatus.FAILED);
            event.setErrorMessage(e.getMessage());
            event.setFailedAtService("account-service");

            // DLQ'ya gönder
            kafkaTemplate.send(KafkaTopics.TRANSACTION_DLQ, event.getTransactionId(),
                    JsonUtil.toJson(event));

            log.warn("İşlem başarısız, DLQ'ya gönderildi - txId: {}, hata: {}",
                    event.getTransactionId(), e.getMessage());
        } catch (Exception dlqError) {
            log.error("DLQ'ya gönderme hatası: {}", dlqError.getMessage());
        }
    }
}
