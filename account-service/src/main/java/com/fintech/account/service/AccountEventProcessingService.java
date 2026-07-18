package com.fintech.account.service;

import com.fintech.account.entity.OutboxEvent;
import com.fintech.account.repository.OutboxEventRepository;
import com.fintech.account.repository.ProcessedEventRepository;
import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.enums.TransferRail;
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

        if (isExternalTransfer(event)) {
            accountService.reserveExternalTransfer(event);
            event.setStatus(TransactionStatus.PROCESSING);
            saveOutbox(event, KafkaTopics.FUNDS_RESERVED);
        } else {
            accountService.processBalanceUpdate(event);
            event.setStatus(TransactionStatus.PROCESSED);
            event.setProcessedTimestamp(Instant.now());
            saveOutbox(event, KafkaTopics.TRANSACTION_CHECKED);
        }

        return true;
    }

    @Transactional
    public boolean processRailResult(TransactionEvent event) {
        validateEventIdentity(event);
        int claimed = processedEventRepository.claimIfNotProcessed(
                "account-rail-settlement-group", event.getTransactionId());
        if (claimed == 0) {
            return false;
        }

        accountService.completeExternalTransfer(event);
        event.setProcessedTimestamp(Instant.now());
        saveOutbox(event, KafkaTopics.TRANSACTION_CHECKED);
        return true;
    }

    private void saveOutbox(TransactionEvent event, String topic) {
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateId(event.getTransactionId())
                .topic(topic)
                .eventKey(event.getTransactionId())
                .payload(JsonUtil.toJson(event))
                .status(OutboxStatus.PENDING)
                .build());
    }

    private boolean isExternalTransfer(TransactionEvent event) {
        return event.getType() == TransactionType.TRANSFER
                && (event.getTransferRail() == TransferRail.EFT || event.getTransferRail() == TransferRail.FAST);
    }

    private void validateEventIdentity(TransactionEvent event) {
        if (event == null || event.getTransactionId() == null || event.getTransactionId().isBlank()) {
            throw new IllegalArgumentException("Transaction event kimliği zorunludur");
        }
    }
}
