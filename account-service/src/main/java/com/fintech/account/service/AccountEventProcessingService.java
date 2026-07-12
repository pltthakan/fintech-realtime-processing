package com.fintech.account.service;

import com.fintech.account.entity.OutboxEvent;
import com.fintech.account.repository.OutboxEventRepository;
import com.fintech.account.repository.ProcessedEventRepository;
import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.OutboxStatus;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AccountEventProcessingService {

    static final String CONSUMER_NAME = "account-service-group";

    private final AccountService accountService;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxEventRepository outboxEventRepository;

    /**
     * Consumer inbox kaydı, bakiye güncellemesi ve outbox kaydı aynı PostgreSQL
     * transaction'ında gerçekleşir. Herhangi biri başarısızsa tamamı rollback olur.
     */
    @Transactional
    public boolean process(TransactionEvent event) {
        validateEventIdentity(event);

        int claimed = processedEventRepository.claimIfNotProcessed(CONSUMER_NAME, event.getTransactionId());
        if (claimed == 0) {
            return false;
        }

        accountService.processBalanceUpdate(event);

        event.setStatus(TransactionStatus.PROCESSED);
        event.setProcessedTimestamp(Instant.now());

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateId(event.getTransactionId())
                .topic(KafkaTopics.TRANSACTION_CHECKED)
                .eventKey(event.getTransactionId())
                .payload(JsonUtil.toJson(event))
                .status(OutboxStatus.PENDING)
                .build());

        return true;
    }

    private void validateEventIdentity(TransactionEvent event) {
        if (event == null || event.getTransactionId() == null || event.getTransactionId().isBlank()) {
            throw new IllegalArgumentException("Transaction event kimliği zorunludur");
        }
    }
}
