package com.fintech.transaction.service;

import com.fintech.common.event.OutboxStatus;
import com.fintech.transaction.entity.OutboxEvent;
import com.fintech.transaction.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private static final int BATCH_SIZE = 50;
    private static final int SEND_TIMEOUT_SECONDS = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, BATCH_SIZE));

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload())
                        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
                event.setLastError(null);
                log.info("Outbox event yayınlandı - id: {}, aggregateId: {}, topic: {}",
                        event.getId(), event.getAggregateId(), event.getTopic());
            } catch (Exception exception) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(truncate(exception.getMessage()));
                log.error("Outbox event yayınlanamadı - id: {}, aggregateId: {}, attempt: {}",
                        event.getId(), event.getAggregateId(), event.getAttempts(), exception);
            }
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown Kafka publish error";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
