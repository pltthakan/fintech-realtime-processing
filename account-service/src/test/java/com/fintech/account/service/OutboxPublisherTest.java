package com.fintech.account.service;

import com.fintech.account.entity.OutboxEvent;
import com.fintech.account.repository.OutboxEventRepository;
import com.fintech.common.event.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(outboxEventRepository, kafkaTemplate);
    }

    @Test
    void marksEventPublishedAfterKafkaAcknowledgement() {
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> result = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload())).thenReturn(result);

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isZero();
    }

    @Test
    void keepsEventPendingAndRecordsFailureForRetry() {
        OutboxEvent event = pendingEvent();
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> result = new CompletableFuture<>();
        result.completeExceptionally(new IllegalStateException("Kafka unavailable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(result);

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("Kafka unavailable");
    }

    private OutboxEvent pendingEvent() {
        return OutboxEvent.builder()
                .aggregateId("tx-1")
                .topic("transaction-checked")
                .eventKey("tx-1")
                .payload("{\"transactionId\":\"tx-1\"}")
                .status(OutboxStatus.PENDING)
                .build();
    }
}
