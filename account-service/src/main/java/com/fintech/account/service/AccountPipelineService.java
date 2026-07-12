package com.fintech.account.service;

import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Kafka Pipeline'ın C adımı.
 * transaction-validated'dan okur → bakiye günceller → transaction-checked'e yazar
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPipelineService {

    private final AccountEventProcessingService accountEventProcessingService;

    @KafkaListener(
            topics = KafkaTopics.TRANSACTION_VALIDATED,
            groupId = "account-service-group",
            concurrency = "3"
    )
    public void processTransaction(String message) {
        TransactionEvent event = JsonUtil.fromJson(message, TransactionEvent.class);

        if (event.getStatus() == TransactionStatus.BLOCKED) {
            log.info("Engellenen işlem atlanıyor - txId: {}, fraudScore: {}",
                    event.getTransactionId(), event.getFraudScore());
            return;
        }

        log.info("Bakiye güncelleme başlıyor - txId: {}, type: {}, amount: {} {}",
                event.getTransactionId(), event.getType(), event.getAmount(), event.getCurrency());

        boolean processed = accountEventProcessingService.process(event);
        if (!processed) {
            log.info("Duplicate transaction event güvenle atlandı - txId: {}", event.getTransactionId());
        }
    }
}
