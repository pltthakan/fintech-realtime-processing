package com.fintech.transaction.service;

import com.fintech.common.dto.request.TransactionRequest;
import com.fintech.common.dto.response.TransactionResponse;
import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionDirection;
import com.fintech.common.enums.TransferRail;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.exception.DuplicateTransactionException;
import com.fintech.common.exception.BusinessException;
import com.fintech.common.exception.ForbiddenException;
import com.fintech.common.exception.ResourceNotFoundException;
import com.fintech.common.util.JsonUtil;
import com.fintech.common.util.IbanUtils;
import com.fintech.common.util.ReferenceGenerator;
import com.fintech.common.util.TransferRoutingPolicy;
import com.fintech.transaction.entity.Transaction;
import com.fintech.transaction.entity.TransactionStatusHistory;
import com.fintech.transaction.repository.TransactionRepository;
import com.fintech.transaction.repository.AccountRoutingView;
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

        ResolvedDestination destination = validateTransactionRequest(request, userId, role);

        // 1. İdempotency kontrolü
        transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .ifPresent(existing -> {
                    throw new DuplicateTransactionException(request.getIdempotencyKey());
                });

        // 2. Transaction oluştur ve DB'ye kaydet
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .sourceAccountId(request.getSourceAccountId())
                .targetAccountId(destination.targetAccountId())
                .beneficiaryIban(destination.beneficiaryIban())
                .beneficiaryName(destination.beneficiaryName())
                .beneficiaryBankCode(destination.beneficiaryBankCode())
                .transferRail(destination.transferRail())
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
                .beneficiaryIban(transaction.getBeneficiaryIban())
                .beneficiaryName(transaction.getBeneficiaryName())
                .beneficiaryBankCode(transaction.getBeneficiaryBankCode())
                .transferRail(transaction.getTransferRail())
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
        TransactionDirection direction = validateTransactionAccess(transaction, authenticatedUserId, role);
        return toResponse(transaction, direction);
    }

    public Page<TransactionResponse> getTransactionsByAccount(
            Long accountId, Long authenticatedUserId, String role, Pageable pageable) {
        if (!isAdministrator(role) && !transactionRepository.existsAccountOwnedBy(accountId, authenticatedUserId)) {
            throw new ForbiddenException();
        }

        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(transaction -> toResponse(transaction, directionForAccount(transaction, accountId)));
    }

    public Page<TransactionResponse> getTransactionsByUser(
            Long userId, Long authenticatedUserId, String role, Pageable pageable) {
        if (!isAdministrator(role) && !Objects.equals(userId, authenticatedUserId)) {
            throw new ForbiddenException();
        }

        Set<Long> ownedAccountIds = Set.copyOf(transactionRepository.findAccountIdsOwnedBy(userId));
        return transactionRepository.findByParticipantUserId(userId, pageable)
                .map(transaction -> toResponse(transaction, directionForAccounts(transaction, ownedAccountIds)));
    }

    public List<TransactionStatusHistory> getTransactionHistory(
            UUID transactionId, Long authenticatedUserId, String role) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("İşlem", "id", transactionId));
        validateTransactionAccess(transaction, authenticatedUserId, role);
        return statusHistoryRepository.findByTransactionIdOrderByCreatedAtAsc(transactionId);
    }

    private ResolvedDestination validateTransactionRequest(TransactionRequest request, Long userId, String role) {
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
                if (request.getTargetAccountId() != null
                        && Objects.equals(request.getSourceAccountId(), request.getTargetAccountId())) {
                    throw badRequest("Kaynak ve hedef hesap aynı olamaz", "SAME_ACCOUNT_TRANSFER");
                }
                validateOwnedActiveAccount(request.getSourceAccountId(), userId, request);
                return resolveTransferDestination(request, userId);
            }
            case PAYMENT, WITHDRAWAL -> {
                requireAccount(request.getSourceAccountId(), "Kaynak hesap zorunludur");
                requireAbsent(request.getTargetAccountId(), "Bu işlem tipi hedef hesap kabul etmez");
                requireNoBeneficiary(request);
                validateOwnedActiveAccount(request.getSourceAccountId(), userId, request);
            }
            case DEPOSIT -> {
                if (!isAdministrator(role)) {
                    throw new BusinessException("Para yatırma yalnızca yetkili fonlama kanalından yapılabilir",
                            "DEPOSIT_REQUIRES_ADMIN", HttpStatus.FORBIDDEN);
                }
                requireAbsent(request.getSourceAccountId(), "Para yatırmada kaynak hesap gönderilmemelidir");
                requireAccount(request.getTargetAccountId(), "Hedef hesap zorunludur");
                requireNoBeneficiary(request);
                validateActiveAccount(request.getTargetAccountId(), request);
            }
        }
        return ResolvedDestination.none(request.getTargetAccountId());
    }

    private ResolvedDestination resolveTransferDestination(TransactionRequest request, Long userId) {
        boolean hasTargetId = request.getTargetAccountId() != null;
        boolean hasIban = request.getBeneficiaryIban() != null && !request.getBeneficiaryIban().isBlank();
        if (hasTargetId == hasIban) {
            throw badRequest(
                    "Transfer için hedef hesap veya alıcı IBAN alanlarından yalnızca biri gönderilmelidir",
                    "TRANSFER_DESTINATION_REQUIRED");
        }

        if (hasTargetId) {
            requireAccount(request.getTargetAccountId(), "Hedef hesap zorunludur");
            if (Objects.equals(request.getSourceAccountId(), request.getTargetAccountId())) {
                throw badRequest("Kaynak ve hedef hesap aynı olamaz", "SAME_ACCOUNT_TRANSFER");
            }
            validateActiveAccount(request.getTargetAccountId(), request);
            TransferRail rail = transactionRepository.existsAccountOwnedBy(request.getTargetAccountId(), userId)
                    ? TransferRail.INTERNAL : TransferRail.HAVALE;
            return new ResolvedDestination(request.getTargetAccountId(), null, null, null, rail);
        }

        String iban = IbanUtils.normalize(request.getBeneficiaryIban());
        AccountRoutingView source = transactionRepository.findAccountForRoutingById(request.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Hesap", "id", request.getSourceAccountId()));
        if (source.getAccountNumber().equals(iban)) {
            throw badRequest("Kaynak IBAN alıcı IBAN ile aynı olamaz", "SAME_ACCOUNT_TRANSFER");
        }

        AccountRoutingView internalTarget = transactionRepository.findAccountForRouting(iban).orElse(null);
        if (internalTarget != null) {
            if (!"ACTIVE".equals(internalTarget.getStatus())
                    || !request.getCurrency().name().equals(internalTarget.getCurrency())) {
                throw badRequest("Alıcı hesabı aktif olmalı ve para birimi eşleşmelidir",
                        "BENEFICIARY_ACCOUNT_MISMATCH");
            }
            TransferRail rail = Objects.equals(internalTarget.getUserId(), userId)
                    ? TransferRail.INTERNAL : TransferRail.HAVALE;
            return new ResolvedDestination(
                    internalTarget.getId(), iban, trimToNull(request.getBeneficiaryName()),
                    IbanUtils.bankCode(iban), rail);
        }

        if (!IbanUtils.isValidTurkishIban(iban)) {
            throw badRequest("Geçerli bir Türkiye IBAN'ı girilmelidir", "INVALID_BENEFICIARY_IBAN");
        }
        if (request.getCurrency() != Currency.TRY) {
            throw badRequest("EFT/FAST simülasyonu yalnızca TRY hesaplarını destekler",
                    "EXTERNAL_TRANSFER_CURRENCY_NOT_SUPPORTED");
        }
        String beneficiaryName = trimToNull(request.getBeneficiaryName());
        if (beneficiaryName == null) {
            throw badRequest("Harici transferlerde alıcı adı zorunludur", "BENEFICIARY_NAME_REQUIRED");
        }

        TransferRail rail = TransferRoutingPolicy.selectExternalRail(request.getAmount(), request.getCurrency());
        return new ResolvedDestination(null, iban, beneficiaryName, IbanUtils.bankCode(iban), rail);
    }

    private void requireNoBeneficiary(TransactionRequest request) {
        if ((request.getBeneficiaryIban() != null && !request.getBeneficiaryIban().isBlank())
                || (request.getBeneficiaryName() != null && !request.getBeneficiaryName().isBlank())
                || request.getTransferRail() != null) {
            throw badRequest("Bu işlem tipi alıcı IBAN bilgisi kabul etmez", "UNEXPECTED_BENEFICIARY");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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

    private TransactionDirection validateTransactionAccess(
            Transaction transaction, Long authenticatedUserId, String role) {
        if (isAdministrator(role)) {
            return defaultDirection(transaction);
        }

        Set<Long> ownedAccountIds = Set.copyOf(
                transactionRepository.findAccountIdsOwnedBy(authenticatedUserId));
        TransactionDirection direction = directionForAccounts(transaction, ownedAccountIds);
        if (direction == null && !Objects.equals(transaction.getUserId(), authenticatedUserId)) {
            throw new ForbiddenException();
        }
        return direction != null ? direction : defaultDirection(transaction);
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
        return toResponse(tx, defaultDirection(tx));
    }

    private TransactionResponse toResponse(Transaction tx, TransactionDirection direction) {
        return TransactionResponse.builder()
                .transactionId(tx.getId().toString())
                .sourceAccountId(tx.getSourceAccountId())
                .targetAccountId(tx.getTargetAccountId())
                .beneficiaryIban(tx.getBeneficiaryIban() == null ? null : IbanUtils.mask(tx.getBeneficiaryIban()))
                .beneficiaryName(tx.getBeneficiaryName())
                .transferRail(tx.getTransferRail())
                .externalReference(tx.getExternalReference())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .type(tx.getType())
                .direction(direction)
                .status(tx.getStatus())
                .fraudScore(tx.getFraudScore())
                .description(tx.getDescription())
                .referenceNumber(tx.getReferenceNumber())
                .createdAt(tx.getCreatedAt())
                .completedAt(tx.getCompletedAt())
                .build();
    }

    private TransactionDirection directionForAccount(Transaction transaction, Long accountId) {
        if (Objects.equals(transaction.getSourceAccountId(), accountId)) {
            return TransactionDirection.DEBIT;
        }
        if (Objects.equals(transaction.getTargetAccountId(), accountId)) {
            return TransactionDirection.CREDIT;
        }
        throw new IllegalStateException("İşlem sorgulanan hesaba ait değil: " + accountId);
    }

    private TransactionDirection directionForAccounts(Transaction transaction, Set<Long> accountIds) {
        boolean ownsSource = transaction.getSourceAccountId() != null
                && accountIds.contains(transaction.getSourceAccountId());
        boolean ownsTarget = transaction.getTargetAccountId() != null
                && accountIds.contains(transaction.getTargetAccountId());

        if (ownsSource && ownsTarget) {
            return TransactionDirection.NEUTRAL;
        }
        if (ownsSource) {
            return TransactionDirection.DEBIT;
        }
        if (ownsTarget) {
            return TransactionDirection.CREDIT;
        }
        return null;
    }

    private TransactionDirection defaultDirection(Transaction transaction) {
        return transaction.getType() == TransactionType.DEPOSIT
                ? TransactionDirection.CREDIT
                : TransactionDirection.DEBIT;
    }

    @Transactional
    public void applyAccountResult(TransactionEvent event) {
        UUID transactionId = UUID.fromString(event.getTransactionId());
        Transaction transaction = transactionRepository.findByIdWithLock(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("İşlem", "id", transactionId));
        transaction.setExternalReference(event.getExternalReference());
        transaction.setErrorMessage(event.getRailFailureReason());

        TransactionStatus desired = event.getStatus() == TransactionStatus.FAILED
                ? TransactionStatus.FAILED : TransactionStatus.PROCESSED;
        TransactionStatus current = transaction.getStatus();
        if (current == TransactionStatus.COMPLETED && desired == TransactionStatus.PROCESSED) {
            transactionRepository.save(transaction);
            return;
        }
        if ((current == TransactionStatus.VALIDATED || current == TransactionStatus.FRAUD_CHECK)
                && desired != TransactionStatus.FAILED) {
            transaction.setStatus(TransactionStatus.CHECKED);
            saveStatusHistory(transactionId, current, TransactionStatus.CHECKED,
                    "fraud-detection-service", "Fraud sonucu hesap olayından doğrulandı");
            current = TransactionStatus.CHECKED;
        }
        if (current != desired) {
            validateStatusTransition(current, desired);
            transaction.setStatus(desired);
            saveStatusHistory(transactionId, current, desired, "account-service",
                    desired == TransactionStatus.FAILED
                            ? "Dış transfer başarısız, rezervasyon serbest bırakıldı"
                            : "Bakiye ve ledger güncellendi");
        }
        transactionRepository.save(transaction);
    }

    /** Ayrı Kafka topic'leri farklı hızlarda işlense de fraud sonucu durumu geriye götürmez. */
    @Transactional
    public void applyFraudResult(TransactionEvent event) {
        UUID transactionId = UUID.fromString(event.getTransactionId());
        Transaction transaction = transactionRepository.findByIdWithLock(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("İşlem", "id", transactionId));
        TransactionStatus desired = Boolean.TRUE.equals(event.getIsBlocked())
                ? TransactionStatus.BLOCKED : TransactionStatus.CHECKED;
        TransactionStatus current = transaction.getStatus();

        if (current == desired) {
            return;
        }
        if (desired == TransactionStatus.CHECKED
                && EnumSet.of(TransactionStatus.PROCESSING, TransactionStatus.PROCESSED,
                        TransactionStatus.COMPLETED, TransactionStatus.FAILED).contains(current)) {
            log.info("Geç gelen fraud sonucu atlandı - txId: {}, current: {}", transactionId, current);
            return;
        }

        validateStatusTransition(current, desired);
        transaction.setStatus(desired);
        transaction.setFraudScore(event.getFraudScore());
        transactionRepository.save(transaction);
        saveStatusHistory(transactionId, current, desired, "fraud-detection-service",
                desired == TransactionStatus.BLOCKED
                        ? "Fraud kontrolünde engellendi. Skor: " + event.getFraudScore()
                        : "Fraud kontrolünden geçti. Skor: " + event.getFraudScore());
    }

    /** Notification olayı önce gelirse eksik ara durumları kilit altında tamamlar. */
    @Transactional
    public void applyNotificationResult(TransactionEvent event) {
        UUID transactionId = UUID.fromString(event.getTransactionId());
        Transaction transaction = transactionRepository.findByIdWithLock(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("İşlem", "id", transactionId));
        TransactionStatus current = transaction.getStatus();
        if (current == TransactionStatus.COMPLETED) {
            return;
        }
        if (current == TransactionStatus.FAILED || current == TransactionStatus.BLOCKED) {
            throw new BusinessException("Başarısız işlem tamamlandı olarak işaretlenemez",
                    "INVALID_STATUS_TRANSITION", HttpStatus.CONFLICT);
        }
        if (current == TransactionStatus.VALIDATED || current == TransactionStatus.FRAUD_CHECK) {
            transaction.setStatus(TransactionStatus.CHECKED);
            saveStatusHistory(transactionId, current, TransactionStatus.CHECKED,
                    "fraud-detection-service", "Fraud sonucu sonraki pipeline olayından doğrulandı");
            current = TransactionStatus.CHECKED;
        }
        if (current == TransactionStatus.CHECKED || current == TransactionStatus.PROCESSING) {
            transaction.setStatus(TransactionStatus.PROCESSED);
            saveStatusHistory(transactionId, current, TransactionStatus.PROCESSED,
                    "account-service", "Bakiye ve ledger güncellendi");
            current = TransactionStatus.PROCESSED;
        }

        validateStatusTransition(current, TransactionStatus.COMPLETED);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(Instant.now());
        transactionRepository.save(transaction);
        saveStatusHistory(transactionId, current, TransactionStatus.COMPLETED,
                "notification-service", "İşlem tamamlandı, bildirim gönderildi");
    }

    private record ResolvedDestination(
            Long targetAccountId,
            String beneficiaryIban,
            String beneficiaryName,
            String beneficiaryBankCode,
            TransferRail transferRail) {

        private static ResolvedDestination none(Long targetAccountId) {
            return new ResolvedDestination(targetAccountId, null, null, null, null);
        }
    }
}
