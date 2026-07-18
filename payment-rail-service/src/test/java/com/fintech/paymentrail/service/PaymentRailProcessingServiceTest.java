package com.fintech.paymentrail.service;

import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.enums.TransferRail;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.util.JsonUtil;
import com.fintech.paymentrail.entity.OutboxEvent;
import com.fintech.paymentrail.entity.PaymentRailAttempt;
import com.fintech.paymentrail.entity.PaymentRailStatus;
import com.fintech.paymentrail.repository.OutboxEventRepository;
import com.fintech.paymentrail.repository.PaymentRailAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentRailProcessingServiceTest {

    @Mock
    private PaymentRailAttemptRepository attemptRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private PaymentRailProcessingService service;

    @BeforeEach
    void setUp() {
        service = new PaymentRailProcessingService(attemptRepository, outboxEventRepository);
    }

    @Test
    void settlesTransferWithoutPersistingPlainIbanAndWritesResultToOutbox() {
        TransactionEvent event = externalTransfer(
                "TR330006100519786457841326", "250.00", TransferRail.FAST);
        when(attemptRepository.findByTransactionId(UUID.fromString(event.getTransactionId())))
                .thenReturn(Optional.empty());

        assertThat(service.process(event)).isTrue();

        ArgumentCaptor<PaymentRailAttempt> attemptCaptor = ArgumentCaptor.forClass(PaymentRailAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        PaymentRailAttempt attempt = attemptCaptor.getValue();
        assertThat(attempt.getStatus()).isEqualTo(PaymentRailStatus.SETTLED);
        assertThat(attempt.getBeneficiaryIbanHash()).hasSize(64).doesNotContain("TR33");
        assertThat(attempt.getBeneficiaryIbanMasked()).isEqualTo("TR33 **** **** **** **** 1326");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent outbox = outboxCaptor.getValue();
        assertThat(outbox.getTopic()).isEqualTo(KafkaTopics.TRANSFER_RAIL_RESULT);
        TransactionEvent result = JsonUtil.fromJson(outbox.getPayload(), TransactionEvent.class);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(result.getExternalReference()).startsWith("RAIL-");
        assertThat(result.getRailFailureReason()).isNull();
    }

    @Test
    void rejectedBeneficiaryProducesFailedResultForReservationRelease() {
        TransactionEvent event = externalTransfer(
                "TR420006100519786457840000", "250.00", TransferRail.FAST);
        when(attemptRepository.findByTransactionId(UUID.fromString(event.getTransactionId())))
                .thenReturn(Optional.empty());

        assertThat(service.process(event)).isTrue();

        ArgumentCaptor<PaymentRailAttempt> attemptCaptor = ArgumentCaptor.forClass(PaymentRailAttempt.class);
        verify(attemptRepository).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getStatus()).isEqualTo(PaymentRailStatus.FAILED);
        assertThat(attemptCaptor.getValue().getFailureReason()).isEqualTo("BENEFICIARY_BANK_REJECTED");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        TransactionEvent result = JsonUtil.fromJson(outboxCaptor.getValue().getPayload(), TransactionEvent.class);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(result.getRailFailureReason()).isEqualTo("BENEFICIARY_BANK_REJECTED");
    }

    @Test
    void duplicateTransactionDoesNotCreateAnotherAttemptOrResult() {
        TransactionEvent event = externalTransfer(
                "TR330006100519786457841326", "250.00", TransferRail.FAST);
        when(attemptRepository.findByTransactionId(UUID.fromString(event.getTransactionId())))
                .thenReturn(Optional.of(PaymentRailAttempt.builder().build()));

        assertThat(service.process(event)).isFalse();

        verify(attemptRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    private TransactionEvent externalTransfer(String iban, String amount, TransferRail rail) {
        return TransactionEvent.builder()
                .transactionId(UUID.randomUUID().toString())
                .sourceAccountId(10L)
                .beneficiaryIban(iban)
                .beneficiaryName("Test Beneficiary")
                .amount(new BigDecimal(amount))
                .currency(Currency.TRY)
                .type(TransactionType.TRANSFER)
                .transferRail(rail)
                .status(TransactionStatus.PROCESSING)
                .build();
    }
}
