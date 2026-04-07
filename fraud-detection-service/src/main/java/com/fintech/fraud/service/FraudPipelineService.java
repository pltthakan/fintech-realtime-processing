package com.fintech.fraud.service;

import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.util.JsonUtil;
import com.fintech.fraud.entity.FraudCheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Kafka Pipeline'ın B adımı.
 * transaction-raw'dan okur → fraud kontrolü yapar → transaction-validated'a yazar
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudPipelineService {

    private final FraudCheckService fraudCheckService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @KafkaListener(
            topics = KafkaTopics.TRANSACTION_RAW,
            groupId = "fraud-detection-group",
            concurrency = "3"
    )
    public void processTransaction(String message) {
        try {
            TransactionEvent event = JsonUtil.fromJson(message, TransactionEvent.class);
            log.info("Fraud kontrolü başlıyor - txId: {}, amount: {} {}",
                    event.getTransactionId(), event.getAmount(), event.getCurrency());

            // Fraud kontrolünü çalıştır
            FraudCheckResult result = fraudCheckService.performFraudCheck(event);

            // Event'i güncelle
            event.setFraudScore(result.getTotalRiskScore());
            event.setIsSuspicious(result.getIsSuspicious());
            event.setIsBlocked(result.getIsBlocked());
            event.setCheckedTimestamp(Instant.now());

            if (result.getIsBlocked()) {
                // Engellenen işlem
                event.setStatus(TransactionStatus.BLOCKED);
                event.setFraudCheckMessage("İşlem fraud kontrolünde engellendi. Risk skoru: " + result.getTotalRiskScore());
                log.warn("İşlem ENGELLENDİ - txId: {}, skor: {}", event.getTransactionId(), result.getTotalRiskScore());

                // DLQ'ya da gönder
                kafkaTemplate.send(KafkaTopics.TRANSACTION_DLQ, event.getTransactionId(),
                        JsonUtil.toJson(event));
            } else {
                // Geçen işlem
                event.setStatus(TransactionStatus.CHECKED);
                event.setFraudCheckMessage("Fraud kontrolünden geçti. Risk skoru: " + result.getTotalRiskScore());
                log.info("İşlem GEÇTİ - txId: {}, skor: {}, suspicious: {}",
                        event.getTransactionId(), result.getTotalRiskScore(), result.getIsSuspicious());
            }

            // Sonucu transaction-validated topic'ine yaz (geçse de engellense de)
            kafkaTemplate.send(KafkaTopics.TRANSACTION_VALIDATED, event.getTransactionId(),
                    JsonUtil.toJson(event))
                    .whenComplete((sendResult, ex) -> {
                        if (ex != null) {
                            log.error("Kafka'ya yazma hatası - txId: {}", event.getTransactionId(), ex);
                        } else {
                            log.info("Fraud sonucu Kafka'ya yazıldı - txId: {}, topic: {}",
                                    event.getTransactionId(), sendResult.getRecordMetadata().topic());
                        }
                    });

        } catch (Exception e) {
            log.error("Fraud pipeline hatası: {}", e.getMessage(), e);
        }
    }
}
