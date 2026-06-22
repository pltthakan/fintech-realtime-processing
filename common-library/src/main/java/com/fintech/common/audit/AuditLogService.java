package com.fintech.common.audit;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Audit kayıtlarını iş verisi transaction'ından bağımsız olarak kalıcılaştırır.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditLogEntry entry) {
        entityManager.persist(AuditLog.builder()
                .actorUserId(entry.actorUserId())
                .actorUsername(entry.actorUsername())
                .actorRole(entry.actorRole())
                .action(entry.action())
                .resourceType(entry.resourceType())
                .resourceId(entry.resourceId())
                .serviceName(entry.serviceName())
                .httpMethod(entry.httpMethod())
                .clientIp(entry.clientIp())
                .details(entry.details())
                .build());
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> getRecent(Pageable pageable) {
        long total = entityManager.createQuery("select count(log) from AuditLog log", Long.class)
                .getSingleResult();
        List<AuditLog> logs = entityManager.createQuery(
                        "select log from AuditLog log order by log.occurredAt desc, log.id desc", AuditLog.class)
                .setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        return new PageImpl<>(logs, pageable, total);
    }
}
