package com.fintech.transaction.service;

import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Pipeline'daki diğer servislerden gelen event'leri dinler
 * ve Transaction tablosundaki durumu günceller.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionEventListener {

    private final TransactionService transactionService;

    /**
     * Fraud Service (B) kontrolünden geçen işlemler
     */
    @KafkaListener(topics = KafkaTopics.TRANSACTION_VALIDATED, groupId = "transaction-status-updater")
    public void onTransactionValidated(String message) {
        TransactionEvent event = JsonUtil.fromJson(message, TransactionEvent.class);
        transactionService.applyFraudResult(event);
    }

    /**
     * Account Service (C) bakiye güncellemesinden geçen işlemler
     */
    @KafkaListener(topics = KafkaTopics.TRANSACTION_CHECKED, groupId = "transaction-status-updater")
    public void onTransactionChecked(String message) {
        TransactionEvent event = JsonUtil.fromJson(message, TransactionEvent.class);
        transactionService.applyAccountResult(event);
    }

    /**
     * Notification Service (D) tamamlanan işlemler
     */
    @KafkaListener(topics = KafkaTopics.TRANSACTION_PROCESSED, groupId = "transaction-status-updater")
    public void onTransactionProcessed(String message) {
        TransactionEvent event = JsonUtil.fromJson(message, TransactionEvent.class);
        transactionService.applyNotificationResult(event);
    }
}
