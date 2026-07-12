package com.fintech.account.repository;

import com.fintech.account.entity.ProcessedEvent;
import com.fintech.account.entity.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {

    @Modifying
    @Query(value = """
            INSERT INTO account_service.processed_events (consumer_name, event_id, processed_at)
            VALUES (:consumerName, :eventId, NOW())
            ON CONFLICT (consumer_name, event_id) DO NOTHING
            """, nativeQuery = true)
    int claimIfNotProcessed(
            @Param("consumerName") String consumerName,
            @Param("eventId") String eventId);
}
