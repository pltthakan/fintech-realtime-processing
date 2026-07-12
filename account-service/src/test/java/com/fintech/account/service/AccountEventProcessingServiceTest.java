package com.fintech.account.service;

import com.fintech.account.entity.OutboxEvent;
import com.fintech.account.repository.OutboxEventRepository;
import com.fintech.account.repository.ProcessedEventRepository;
import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.TransactionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountEventProcessingServiceTest {

    @Mock
    private AccountService accountService;
    @Mock
    private ProcessedEventRepository processedEventRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private AccountEventProcessingService service;

    @BeforeEach
    void setUp() {
        service = new AccountEventProcessingService(
                accountService, processedEventRepository, outboxEventRepository);
    }

    @Test
    void processesBalanceAndCreatesOutboxEventWhenEventIsNew() {
        TransactionEvent event = transactionEvent();
        when(processedEventRepository.claimIfNotProcessed(
                AccountEventProcessingService.CONSUMER_NAME, event.getTransactionId()))
                .thenReturn(1);

        boolean processed = service.process(event);

        assertThat(processed).isTrue();
        assertThat(event.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(event.getProcessedTimestamp()).isNotNull();
        verify(accountService).processBalanceUpdate(event);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent outboxEvent = captor.getValue();
        assertThat(outboxEvent.getAggregateId()).isEqualTo(event.getTransactionId());
        assertThat(outboxEvent.getTopic()).isEqualTo(KafkaTopics.TRANSACTION_CHECKED);
        assertThat(outboxEvent.getEventKey()).isEqualTo(event.getTransactionId());
        assertThat(outboxEvent.getPayload()).contains("\"status\":\"PROCESSED\"");
    }

    @Test
    void skipsBalanceAndOutboxWhenEventWasAlreadyProcessed() {
        TransactionEvent event = transactionEvent();
        when(processedEventRepository.claimIfNotProcessed(
                AccountEventProcessingService.CONSUMER_NAME, event.getTransactionId()))
                .thenReturn(0);

        boolean processed = service.process(event);

        assertThat(processed).isFalse();
        verifyNoInteractions(accountService, outboxEventRepository);
    }

    private TransactionEvent transactionEvent() {
        return TransactionEvent.builder()
                .transactionId("a949a93a-e3f8-4a90-b728-e61ee6c40d60")
                .sourceAccountId(10L)
                .targetAccountId(20L)
                .userId(1L)
                .amount(new BigDecimal("125.50"))
                .currency(Currency.TRY)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.CHECKED)
                .build();
    }
}
