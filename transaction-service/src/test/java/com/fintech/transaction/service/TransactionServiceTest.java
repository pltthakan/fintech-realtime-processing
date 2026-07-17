package com.fintech.transaction.service;

import com.fintech.common.dto.request.TransactionRequest;
import com.fintech.common.dto.response.TransactionResponse;
import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.exception.BusinessException;
import com.fintech.transaction.entity.Transaction;
import com.fintech.transaction.repository.TransactionRepository;
import com.fintech.transaction.repository.TransactionStatusHistoryRepository;
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

    private TransactionService service;

    @BeforeEach
    void setUp() {
        service = new TransactionService(transactionRepository, statusHistoryRepository, outboxService);
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

        when(transactionRepository.existsAccountOwnedBy(10L, 7L)).thenReturn(true);
        when(transactionRepository.existsAccount(10L)).thenReturn(true);
        when(transactionRepository.existsAccount(20L)).thenReturn(true);
        when(transactionRepository.existsActiveAccountWithCurrency(10L, "TRY")).thenReturn(true);
        when(transactionRepository.existsActiveAccountWithCurrency(20L, "TRY")).thenReturn(true);
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
}
