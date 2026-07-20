package com.fintech.transaction.service;

import com.fintech.common.dto.internal.AccountSnapshot;
import com.fintech.common.dto.request.TransactionRequest;
import com.fintech.common.dto.response.TransactionResponse;
import com.fintech.common.enums.Currency;
import com.fintech.common.enums.AccountStatus;
import com.fintech.common.enums.TransactionDirection;
import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.enums.TransferRail;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.exception.BusinessException;
import com.fintech.common.util.JsonUtil;
import com.fintech.transaction.entity.Transaction;
import com.fintech.transaction.client.AccountDirectoryClient;
import com.fintech.transaction.repository.TransactionRepository;
import com.fintech.transaction.repository.TransactionStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionStatusHistoryRepository statusHistoryRepository;
    @Mock
    private OutboxService outboxService;
    @Mock
    private AccountDirectoryClient accountDirectoryClient;

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(
                transactionRepository, statusHistoryRepository, outboxService, accountDirectoryClient);
    }

    @Test
    void storesInitialKafkaEventInOutboxInsideTransaction() {
        UUID transactionId = UUID.fromString("2f1590b7-d488-44bf-b318-e81035e03c21");
        TransactionRequest request = TransactionRequest.builder()
                .sourceAccountId(10L)
                .targetAccountId(20L)
                .amount(new BigDecimal("250.00"))
                .currency(Currency.TRY)
                .type(TransactionType.TRANSFER)
                .idempotencyKey("client-request-1")
                .build();

        when(accountDirectoryClient.getAccount(10L)).thenReturn(account(10L, 7L, "TR100006100000000000000001"));
        when(accountDirectoryClient.getAccount(20L)).thenReturn(account(20L, 8L, "TR100006100000000000000002"));
        when(transactionRepository.findByIdempotencyKey("client-request-1")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            if (transaction.getId() == null) {
                transaction.setId(transactionId);
            }
            return transaction;
        });

        TransactionResponse response = service.createTransaction(request, 7L, "demo-user", "USER");

        assertThat(response.getTransactionId()).isEqualTo(transactionId.toString());
        assertThat(response.getStatus()).isEqualTo(TransactionStatus.VALIDATED);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).add(
                eq(transactionId.toString()),
                eq(KafkaTopics.TRANSACTION_RAW),
                eq(transactionId.toString()),
                payloadCaptor.capture());
        assertThat(payloadCaptor.getValue())
                .contains("\"transactionId\":\"" + transactionId + "\"")
                .contains("\"status\":\"VALIDATED\"");

        verify(statusHistoryRepository, times(2)).save(any());
        verifyNoMoreInteractions(outboxService);
    }

    @Test
    void rejectsSameAccountTransferBeforePersistingMoneyMovement() {
        TransactionRequest request = TransactionRequest.builder()
                .sourceAccountId(10L)
                .targetAccountId(10L)
                .amount(new BigDecimal("25.00"))
                .currency(Currency.TRY)
                .type(TransactionType.TRANSFER)
                .idempotencyKey("same-account")
                .build();

        assertThatThrownBy(() -> service.createTransaction(request, 7L, "demo-user", "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("SAME_ACCOUNT_TRANSFER");

        verify(transactionRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void resolvesExternalTurkishIbanToFastAndPublishesRoutingMetadata() {
        UUID transactionId = UUID.randomUUID();
        String beneficiaryIban = "TR330006100519786457841326";
        TransactionRequest request = TransactionRequest.builder()
                .sourceAccountId(10L)
                .beneficiaryIban(beneficiaryIban)
                .beneficiaryName("Ayşe Yılmaz")
                .amount(new BigDecimal("999.99"))
                .currency(Currency.TRY)
                .type(TransactionType.TRANSFER)
                .idempotencyKey("external-fast-1")
                .build();
        when(accountDirectoryClient.getAccount(10L))
                .thenReturn(account(10L, 7L, "TR220006100519786457841325"));
        when(accountDirectoryClient.findAccountByIban(beneficiaryIban)).thenReturn(Optional.empty());
        when(transactionRepository.findByIdempotencyKey("external-fast-1")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction transaction = invocation.getArgument(0);
            transaction.setId(transactionId);
            return transaction;
        });

        TransactionResponse response = service.createTransaction(request, 7L, "demo-user", "USER");

        assertThat(response.getTargetAccountId()).isNull();
        assertThat(response.getTransferRail()).isEqualTo(TransferRail.FAST);
        assertThat(response.getBeneficiaryIban()).isEqualTo("TR33 **** **** **** **** 1326");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxService).add(
                eq(transactionId.toString()), eq(KafkaTopics.TRANSACTION_RAW),
                eq(transactionId.toString()), payloadCaptor.capture());
        TransactionEvent event = JsonUtil.fromJson(payloadCaptor.getValue(), TransactionEvent.class);
        assertThat(event.getTargetAccountId()).isNull();
        assertThat(event.getTransferRail()).isEqualTo(TransferRail.FAST);
        assertThat(event.getBeneficiaryIban()).isEqualTo(beneficiaryIban);
        assertThat(event.getBeneficiaryName()).isEqualTo("Ayşe Yılmaz");
    }

    @Test
    void rejectsUserInitiatedDeposit() {
        TransactionRequest request = TransactionRequest.builder()
                .targetAccountId(20L)
                .amount(new BigDecimal("25.00"))
                .currency(Currency.TRY)
                .type(TransactionType.DEPOSIT)
                .idempotencyKey("mint-attempt")
                .build();

        assertThatThrownBy(() -> service.createTransaction(request, 7L, "demo-user", "USER"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("DEPOSIT_REQUIRES_ADMIN");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void appliesAllowedStatusTransitionOnlyOnce() {
        UUID id = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(id)
                .userId(7L)
                .amount(new BigDecimal("10.00"))
                .currency(Currency.TRY)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.VALIDATED)
                .idempotencyKey("status-once")
                .build();
        when(transactionRepository.findByIdWithLock(id)).thenReturn(Optional.of(transaction));

        service.updateTransactionStatus(id, TransactionStatus.CHECKED, "fraud", "ok");
        service.updateTransactionStatus(id, TransactionStatus.CHECKED, "fraud", "duplicate");

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.CHECKED);
        verify(statusHistoryRepository, times(1)).save(any());
        verify(transactionRepository, times(1)).save(transaction);
    }

    @Test
    void rejectsOutOfOrderStatusRegression() {
        UUID id = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(id)
                .userId(7L)
                .amount(new BigDecimal("10.00"))
                .currency(Currency.TRY)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PROCESSED)
                .idempotencyKey("status-regression")
                .build();
        when(transactionRepository.findByIdWithLock(id)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> service.updateTransactionStatus(
                id, TransactionStatus.CHECKED, "fraud", "late event"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("INVALID_STATUS_TRANSITION");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void listsSameTransferAsDebitForSenderAndCreditForRecipient() {
        Transaction transfer = completedTransfer(1L, 23L, 3L);
        PageRequest pageRequest = PageRequest.of(0, 20);
        PageImpl<Transaction> page = new PageImpl<>(List.of(transfer), pageRequest, 1);

        when(accountDirectoryClient.getAccount(1L))
                .thenReturn(account(1L, 3L, "TR100006100000000000000001"));
        when(accountDirectoryClient.getAccount(23L))
                .thenReturn(account(23L, 22L, "TR100006100000000000000023"));
        when(transactionRepository.findByAccountIdOrderByCreatedAtDesc(1L, pageRequest)).thenReturn(page);
        when(transactionRepository.findByAccountIdOrderByCreatedAtDesc(23L, pageRequest)).thenReturn(page);

        TransactionResponse senderView = service
                .getTransactionsByAccount(1L, 3L, "USER", pageRequest)
                .getContent().get(0);
        TransactionResponse recipientView = service
                .getTransactionsByAccount(23L, 22L, "USER", pageRequest)
                .getContent().get(0);

        assertThat(senderView.getDirection()).isEqualTo(TransactionDirection.DEBIT);
        assertThat(recipientView.getDirection()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(senderView.getTransactionId()).isEqualTo(recipientView.getTransactionId());
    }

    @Test
    void includesIncomingTransferInRecipientUserTimeline() {
        Transaction transfer = completedTransfer(1L, 23L, 3L);
        PageRequest pageRequest = PageRequest.of(0, 5);

        when(accountDirectoryClient.getAccountIdsByUser(22L)).thenReturn(List.of(23L));
        when(transactionRepository.findByParticipantAccountIds(Set.of(23L), pageRequest))
                .thenReturn(new PageImpl<>(List.of(transfer), pageRequest, 1));

        TransactionResponse recipientView = service
                .getTransactionsByUser(22L, 22L, "USER", pageRequest)
                .getContent().get(0);

        assertThat(recipientView.getDirection()).isEqualTo(TransactionDirection.CREDIT);
        assertThat(recipientView.getAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void allowsRecipientToOpenIncomingTransactionDetails() {
        Transaction transfer = completedTransfer(1L, 23L, 3L);

        when(transactionRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
        when(accountDirectoryClient.getAccountIdsByUser(22L)).thenReturn(List.of(23L));

        TransactionResponse response = service.getTransactionById(transfer.getId(), 22L, "USER");

        assertThat(response.getDirection()).isEqualTo(TransactionDirection.CREDIT);
    }

    @Test
    void convergesToCompletedWhenCrossTopicEventsArriveOutOfOrder() {
        UUID id = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(id)
                .userId(7L)
                .amount(new BigDecimal("10.00"))
                .currency(Currency.TRY)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.VALIDATED)
                .idempotencyKey("cross-topic-order")
                .build();
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(id.toString())
                .status(TransactionStatus.PROCESSED)
                .externalReference("RAIL-ORDER-1")
                .build();
        when(transactionRepository.findByIdWithLock(id)).thenReturn(Optional.of(transaction));

        service.applyNotificationResult(event);
        service.applyAccountResult(event);
        service.applyFraudResult(event);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.getCompletedAt()).isNotNull();
        assertThat(transaction.getExternalReference()).isEqualTo("RAIL-ORDER-1");
        verify(statusHistoryRepository, times(3)).save(any());
    }

    private Transaction completedTransfer(Long sourceAccountId, Long targetAccountId, Long initiatorUserId) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .userId(initiatorUserId)
                .sourceAccountId(sourceAccountId)
                .targetAccountId(targetAccountId)
                .amount(new BigDecimal("5000.00"))
                .currency(Currency.TRY)
                .type(TransactionType.TRANSFER)
                .transferRail(TransferRail.HAVALE)
                .status(TransactionStatus.COMPLETED)
                .referenceNumber("FTK-TEST-5000")
                .idempotencyKey("test-" + UUID.randomUUID())
                .build();
    }

    private AccountSnapshot account(Long accountId, Long userId, String accountNumber) {
        return AccountSnapshot.builder()
                .accountId(accountId)
                .userId(userId)
                .accountNumber(accountNumber)
                .currency(Currency.TRY)
                .status(AccountStatus.ACTIVE)
                .build();
    }
}
