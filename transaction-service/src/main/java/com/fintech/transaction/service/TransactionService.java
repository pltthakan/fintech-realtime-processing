package com.fintech.transaction.service;

import com.fintech.common.dto.request.TransactionRequest;
import com.fintech.common.dto.response.TransactionResponse;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.exception.DuplicateTransactionException;
import com.fintech.common.exception.BusinessException;
import com.fintech.common.exception.ForbiddenException;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED_TRANSITIONS = buildTransitions();

    private final TransactionRepository transactionRepository;
    private final TransactionStatusHistoryRepository statusHistoryRepository;
    private final OutboxService outboxService;

    /**
     * Yeni işlem oluştur ve Kafka pipeline'ına gönder.
     * Pipeline: transaction-raw → Fraud Service (B)
     */
    @Transactional
    public TransactionResponse createTransaction(
            TransactionRequest request, Long userId, String username, String role) {

        validateTransactionRequest(request, userId, role);

        // 1. İdempotency kontrolü
        transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .ifPresent(existing -> {
                    throw new DuplicateTransactionException(request.getIdempotencyKey());
                });

        // 2. Transaction oluştur ve DB'ye kaydet
        Transaction transaction = Transaction.builder()
                .userId(userId)
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

        // 5. Kafka event oluştur ve aynı DB transaction'ında outbox'a kaydet
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
                .initiatorRole(role)
                .rawTimestamp(Instant.now())
                .validatedTimestamp(Instant.now())
                .build();

        String eventJson = JsonUtil.toJson(event);
        outboxService.add(
                transaction.getId().toString(),
                KafkaTopics.TRANSACTION_RAW,
                transaction.getId().toString(),
                eventJson
        );

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
        Transaction transaction = transactionRepository.findByIdWithLock(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("İşlem", "id", transactionId));

        TransactionStatus oldStatus = transaction.getStatus();
        if (oldStatus == newStatus) {
            log.info("İşlem durumu zaten uygulanmış - txId: {}, status: {}", transactionId, newStatus);
            return;
        }
        validateStatusTransition(oldStatus, newStatus);
        transaction.setStatus(newStatus);

        if (newStatus == TransactionStatus.COMPLETED) {
            transaction.setCompletedAt(Instant.now());
        }

        transactionRepository.save(transaction);
        saveStatusHistory(transactionId, oldStatus, newStatus, serviceName, message);

        log.info("İşlem durumu güncellendi - txId: {}, {} → {}", transactionId, oldStatus, newStatus);
    }

    public TransactionResponse getTransactionById(UUID id, Long authenticatedUserId, String role) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("İşlem", "id", id));
        validateTransactionOwnership(transaction, authenticatedUserId, role);
        return toResponse(transaction);
    }

    public Page<TransactionResponse> getTransactionsByAccount(
            Long accountId, Long authenticatedUserId, String role, Pageable pageable) {
        if (!isAdministrator(role) && !transactionRepository.existsAccountOwnedBy(accountId, authenticatedUserId)) {
            throw new ForbiddenException();
        }

        Page<Transaction> transactions = isAdministrator(role)
                ? transactionRepository.findBySourceAccountIdOrderByCreatedAtDesc(accountId, pageable)
                : transactionRepository.findBySourceAccountIdAndUserIdOrderByCreatedAtDesc(
                        accountId, authenticatedUserId, pageable);
        return transactions
                .map(this::toResponse);
    }

    public List<TransactionStatusHistory> getTransactionHistory(
            UUID transactionId, Long authenticatedUserId, String role) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("İşlem", "id", transactionId));
        validateTransactionOwnership(transaction, authenticatedUserId, role);
        return statusHistoryRepository.findByTransactionIdOrderByCreatedAtAsc(transactionId);
    }

    private void validateTransactionRequest(TransactionRequest request, Long userId, String role) {
        if (userId == null || userId <= 0) {
            throw new ForbiddenException();
        }

        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw badRequest("Idempotency anahtarı zorunludur", "IDEMPOTENCY_KEY_REQUIRED");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || request.getAmount().scale() > 2) {
            throw badRequest("Tutar pozitif ve en fazla iki ondalıklı olmalıdır", "INVALID_AMOUNT");
        }
        if (request.getCurrency() == null || request.getType() == null) {
            throw badRequest("Para birimi ve işlem tipi zorunludur", "TRANSACTION_FIELDS_REQUIRED");
        }

        switch (request.getType()) {
            case TRANSFER -> {
                requireAccount(request.getSourceAccountId(), "Kaynak hesap zorunludur");
                requireAccount(request.getTargetAccountId(), "Hedef hesap zorunludur");
                if (Objects.equals(request.getSourceAccountId(), request.getTargetAccountId())) {
                    throw badRequest("Kaynak ve hedef hesap aynı olamaz", "SAME_ACCOUNT_TRANSFER");
                }
                validateOwnedActiveAccount(request.getSourceAccountId(), userId, request);
                validateActiveAccount(request.getTargetAccountId(), request);
            }
            case PAYMENT, WITHDRAWAL -> {
                requireAccount(request.getSourceAccountId(), "Kaynak hesap zorunludur");
                requireAbsent(request.getTargetAccountId(), "Bu işlem tipi hedef hesap kabul etmez");
                validateOwnedActiveAccount(request.getSourceAccountId(), userId, request);
            }
            case DEPOSIT -> {
                if (!isAdministrator(role)) {
                    throw new BusinessException("Para yatırma yalnızca yetkili fonlama kanalından yapılabilir",
                            "DEPOSIT_REQUIRES_ADMIN", HttpStatus.FORBIDDEN);
                }
                requireAbsent(request.getSourceAccountId(), "Para yatırmada kaynak hesap gönderilmemelidir");
                requireAccount(request.getTargetAccountId(), "Hedef hesap zorunludur");
                validateActiveAccount(request.getTargetAccountId(), request);
            }
        }
    }

    private void validateOwnedActiveAccount(Long accountId, Long userId, TransactionRequest request) {
        if (!transactionRepository.existsAccountOwnedBy(accountId, userId)) {
            throw new ForbiddenException();
        }
        validateActiveAccount(accountId, request);
    }

    private void validateActiveAccount(Long accountId, TransactionRequest request) {
        if (!transactionRepository.existsAccount(accountId)) {
            throw new ResourceNotFoundException("Hesap", "id", accountId);
        }
        if (!transactionRepository.existsActiveAccountWithCurrency(accountId, request.getCurrency().name())) {
            throw badRequest("Hesap aktif olmalı ve işlem para birimiyle eşleşmelidir",
                    "ACCOUNT_CURRENCY_OR_STATUS_MISMATCH");
        }
    }

    private void requireAccount(Long accountId, String message) {
        if (accountId == null || accountId <= 0) {
            throw badRequest(message, "ACCOUNT_REQUIRED");
        }
    }

    private void requireAbsent(Long accountId, String message) {
        if (accountId != null) {
            throw badRequest(message, "UNEXPECTED_ACCOUNT");
        }
    }

    private BusinessException badRequest(String message, String code) {
        return new BusinessException(message, code, HttpStatus.BAD_REQUEST);
    }

    private void validateStatusTransition(TransactionStatus current, TransactionStatus next) {
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new BusinessException(
                    "Geçersiz işlem durumu geçişi: " + current + " → " + next,
                    "INVALID_STATUS_TRANSITION",
                    HttpStatus.CONFLICT);
        }
    }

    private static Map<TransactionStatus, Set<TransactionStatus>> buildTransitions() {
        Map<TransactionStatus, Set<TransactionStatus>> transitions = new EnumMap<>(TransactionStatus.class);
        transitions.put(TransactionStatus.PENDING,
                EnumSet.of(TransactionStatus.VALIDATED, TransactionStatus.FAILED, TransactionStatus.CANCELLED));
        transitions.put(TransactionStatus.VALIDATED,
                EnumSet.of(TransactionStatus.FRAUD_CHECK, TransactionStatus.CHECKED,
                        TransactionStatus.BLOCKED, TransactionStatus.FAILED, TransactionStatus.CANCELLED));
        transitions.put(TransactionStatus.FRAUD_CHECK,
                EnumSet.of(TransactionStatus.CHECKED, TransactionStatus.BLOCKED, TransactionStatus.FAILED));
        transitions.put(TransactionStatus.CHECKED,
                EnumSet.of(TransactionStatus.PROCESSING, TransactionStatus.PROCESSED,
                        TransactionStatus.FAILED, TransactionStatus.CANCELLED));
        transitions.put(TransactionStatus.PROCESSING,
                EnumSet.of(TransactionStatus.PROCESSED, TransactionStatus.FAILED));
        transitions.put(TransactionStatus.PROCESSED,
                EnumSet.of(TransactionStatus.COMPLETED, TransactionStatus.FAILED));
        return Map.copyOf(transitions);
    }

    private void validateTransactionOwnership(Transaction transaction, Long authenticatedUserId, String role) {
        if (!isAdministrator(role) && !java.util.Objects.equals(transaction.getUserId(), authenticatedUserId)) {
            throw new ForbiddenException();
        }
    }

    private boolean isAdministrator(String role) {
        return "ADMIN".equals(role);
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
