package com.fintech.transaction.service;

import com.fintech.common.event.OutboxStatus;
import com.fintech.transaction.entity.OutboxEvent;
import com.fintech.transaction.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;

    public void add(String aggregateId, String topic, String eventKey, String payload) {
        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateId(aggregateId)
                .topic(topic)
                .eventKey(eventKey)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .build());
    }
}
