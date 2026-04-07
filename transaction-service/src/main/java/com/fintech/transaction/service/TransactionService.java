package com.fintech.transaction.service;

import com.fintech.common.dto.request.TransactionRequest;
import com.fintech.common.dto.response.TransactionResponse;
import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.exception.DuplicateTransactionException;
import com.fintech.common.exception.ResourceNotFoundException;
import com.fintech.common.util.JsonUtil;
import com.fintech.common.util.ReferenceGenerator;
import com.fintech.transaction.entity.Transaction;
import com.fintech.transaction.entity.TransactionStatusHistory;
import com.fintech.transaction.repository.TransactionRepository;
import com.fintech.transaction.repository.TransactionStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionStatusHistoryRepository statusHistoryRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Yeni işlem oluştur ve Kafka pipeline'ına gönder.
     * Pipeline: transaction-raw → Fraud Service (B)
     */
    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request, Long userId, String username) {

        // 1. İdempotency kontrolü
        if (request.getIdempotencyKey() != null) {
            transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .ifPresent(existing -> {
                        throw new DuplicateTransactionException(request.getIdempotencyKey());
                    });
        }

        // 2. Transaction oluştur ve DB'ye kaydet
        Transaction transaction = Transaction.builder()
                .sourceAccountId(request.getSourceAccountId())
                .targetAccountId(request.getTargetAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .type(request.getType())
                .status(TransactionStatus.PENDING)
                .description(request.getDescription())
                .referenceNumber(ReferenceGenerator.generate())
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        transaction = transactionRepository.save(transaction);

        // 3. Durum geçmişine kaydet
        saveStatusHistory(transaction.getId(), null, TransactionStatus.PENDING, "transaction-service", "İşlem oluşturuldu");

        // 4. İlk validasyonları yap
        transaction.setStatus(TransactionStatus.VALIDATED);
        transaction = transactionRepository.save(transaction);
        saveStatusHistory(transaction.getId(), TransactionStatus.PENDING, TransactionStatus.VALIDATED, "transaction-service", "İlk validasyon tamamlandı");

        // 5. Kafka event oluştur ve transaction-raw topic'ine gönder
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(transaction.getId().toString())
                .sourceAccountId(transaction.getSourceAccountId())
                .targetAccountId(transaction.getTargetAccountId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .type(transaction.getType())
                .status(TransactionStatus.VALIDATED)
                .description(transaction.getDescription())
                .referenceNumber(transaction.getReferenceNumber())
                .idempotencyKey(transaction.getIdempotencyKey())
                .userId(userId)
                .username(username)
                .rawTimestamp(Instant.now())
                .validatedTimestamp(Instant.now())
                .build();

        String eventJson = JsonUtil.toJson(event);
        final Transaction savedTx = transaction;
        kafkaTemplate.send(KafkaTopics.TRANSACTION_RAW, savedTx.getId().toString(), eventJson)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka'ya yazma hatası - txId: {}, error: {}", savedTx.getId(), ex.getMessage());
                    } else {
                        log.info("İşlem Kafka'ya gönderildi - txId: {}, topic: {}, partition: {}",
                                savedTx.getId(),
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition());
                    }
                });

        log.info("Yeni işlem oluşturuldu - txId: {}, ref: {}, amount: {} {}, type: {}",
                transaction.getId(), transaction.getReferenceNumber(),
                transaction.getAmount(), transaction.getCurrency(), transaction.getType());

        return toResponse(transaction);
    }

    /**
     * İşlem durumunu güncelle (diğer servislerden gelen Kafka event'leri için)
     */
    @Transactional
    public void updateTransactionStatus(UUID transactionId, TransactionStatus newStatus, String serviceName, String message) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("İşlem", "id", transactionId));

        TransactionStatus oldStatus = transaction.getStatus();
        transaction.setStatus(newStatus);

        if (newStatus == TransactionStatus.COMPLETED) {
            transaction.setCompletedAt(Instant.now());
        }

        transactionRepository.save(transaction);
        saveStatusHistory(transactionId, oldStatus, newStatus, serviceName, message);

        log.info("İşlem durumu güncellendi - txId: {}, {} → {}", transactionId, oldStatus, newStatus);
    }

    public TransactionResponse getTransactionById(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("İşlem", "id", id));
        return toResponse(transaction);
    }

    public Page<TransactionResponse> getTransactionsByAccount(Long accountId, Pageable pageable) {
        return transactionRepository.findBySourceAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(this::toResponse);
    }

    public List<TransactionStatusHistory> getTransactionHistory(UUID transactionId) {
        return statusHistoryRepository.findByTransactionIdOrderByCreatedAtAsc(transactionId);
    }

    private void saveStatusHistory(UUID txId, TransactionStatus prev, TransactionStatus next, String service, String msg) {
        statusHistoryRepository.save(TransactionStatusHistory.builder()
                .transactionId(txId)
                .previousStatus(prev != null ? prev.name() : null)
                .newStatus(next.name())
                .serviceName(service)
                .message(msg)
                .build());
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
                .transactionId(tx.getId().toString())
                .sourceAccountId(tx.getSourceAccountId())
                .targetAccountId(tx.getTargetAccountId())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .type(tx.getType())
                .status(tx.getStatus())
                .fraudScore(tx.getFraudScore())
                .description(tx.getDescription())
                .referenceNumber(tx.getReferenceNumber())
                .createdAt(tx.getCreatedAt())
                .completedAt(tx.getCompletedAt())
                .build();
    }
}