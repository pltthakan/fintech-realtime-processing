package com.fintech.paymentrail.service;

import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRailPipelineService {

    private final PaymentRailProcessingService processingService;

    @KafkaListener(
            topics = KafkaTopics.FUNDS_RESERVED,
            groupId = "payment-rail-service-group",
            concurrency = "3"
    )
    public void processReservedFunds(String message) {
        TransactionEvent event = JsonUtil.fromJson(message, TransactionEvent.class);
        boolean processed = processingService.process(event);
        if (!processed) {
            log.info("Duplicate payment rail isteği güvenle atlandı - txId: {}", event.getTransactionId());
        }
    }
}
