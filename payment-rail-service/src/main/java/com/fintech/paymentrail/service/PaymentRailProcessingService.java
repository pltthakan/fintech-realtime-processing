package com.fintech.paymentrail.service;

import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.enums.TransferRail;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.OutboxStatus;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.exception.BusinessException;
import com.fintech.common.util.IbanUtils;
import com.fintech.common.util.JsonUtil;
import com.fintech.common.util.TransferRoutingPolicy;
import com.fintech.paymentrail.entity.OutboxEvent;
import com.fintech.paymentrail.entity.PaymentRailAttempt;
import com.fintech.paymentrail.entity.PaymentRailStatus;
import com.fintech.paymentrail.repository.OutboxEventRepository;
import com.fintech.paymentrail.repository.PaymentRailAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentRailProcessingService {

    private final PaymentRailAttemptRepository attemptRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public boolean process(TransactionEvent event) {
        validate(event);
        UUID transactionId = UUID.fromString(event.getTransactionId());
        if (attemptRepository.findByTransactionId(transactionId).isPresent()) {
            return false;
        }

        String iban = IbanUtils.normalize(event.getBeneficiaryIban());
        String externalReference = "RAIL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        boolean rejected = iban.endsWith("0000");
        String failureReason = rejected ? "BENEFICIARY_BANK_REJECTED" : null;

        attemptRepository.save(PaymentRailAttempt.builder()
                .transactionId(transactionId)
                .externalReference(externalReference)
                .rail(event.getTransferRail())
                .status(rejected ? PaymentRailStatus.FAILED : PaymentRailStatus.SETTLED)
                .beneficiaryIbanHash(sha256(iban))
                .beneficiaryIbanMasked(IbanUtils.mask(iban))
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .failureReason(failureReason)
                .build());

        event.setExternalReference(externalReference);
        event.setRailFailureReason(failureReason);
        event.setStatus(rejected ? TransactionStatus.FAILED : TransactionStatus.PROCESSED);

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateId(event.getTransactionId())
                .topic(KafkaTopics.TRANSFER_RAIL_RESULT)
                .eventKey(event.getTransactionId())
                .payload(JsonUtil.toJson(event))
                .status(OutboxStatus.PENDING)
                .build());
        return true;
    }

    private void validate(TransactionEvent event) {
        if (event == null || event.getTransactionId() == null
                || event.getType() != TransactionType.TRANSFER
                || event.getSourceAccountId() == null
                || event.getTargetAccountId() != null
                || event.getBeneficiaryIban() == null
                || event.getBeneficiaryName() == null || event.getBeneficiaryName().isBlank()
                || event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) <= 0
                || event.getCurrency() == null
                || !IbanUtils.isValidTurkishIban(event.getBeneficiaryIban())
                || (event.getTransferRail() != TransferRail.EFT && event.getTransferRail() != TransferRail.FAST)) {
            throw new BusinessException("Geçersiz payment rail olayı", "INVALID_PAYMENT_RAIL_EVENT");
        }
        TransferRail expectedRail = TransferRoutingPolicy.selectExternalRail(event.getAmount(), event.getCurrency());
        if (expectedRail != event.getTransferRail()) {
            throw new BusinessException("Transfer kanalı tutar politikasıyla eşleşmiyor",
                    "PAYMENT_RAIL_POLICY_MISMATCH");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 desteklenmiyor", exception);
        }
    }
}
