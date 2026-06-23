package com.fintech.common.audit;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    public Page<AuditLog> getRecent(Pageable pageable, String actorUsername,
                                    AuditAction action, String resourceType) {
        List<String> filters = new ArrayList<>();
        if (actorUsername != null && !actorUsername.isBlank()) {
            filters.add("lower(log.actorUsername) like lower(:actorUsername)");
        }
        if (action != null) {
            filters.add("log.action = :action");
        }
        if (resourceType != null && !resourceType.isBlank()) {
            filters.add("log.resourceType = :resourceType");
        }

        String whereClause = filters.isEmpty() ? "" : " where " + String.join(" and ", filters);
        var countQuery = entityManager.createQuery("select count(log) from AuditLog log" + whereClause, Long.class);
        var contentQuery = entityManager.createQuery(
                "select log from AuditLog log" + whereClause + " order by log.occurredAt desc, log.id desc", AuditLog.class);

        if (actorUsername != null && !actorUsername.isBlank()) {
            String searchTerm = "%" + actorUsername.trim() + "%";
            countQuery.setParameter("actorUsername", searchTerm);
            contentQuery.setParameter("actorUsername", searchTerm);
        }
        if (action != null) {
            countQuery.setParameter("action", action);
            contentQuery.setParameter("action", action);
        }
        if (resourceType != null && !resourceType.isBlank()) {
            String normalizedResourceType = resourceType.trim().toUpperCase();
            countQuery.setParameter("resourceType", normalizedResourceType);
            contentQuery.setParameter("resourceType", normalizedResourceType);
        }

        long total = countQuery.getSingleResult();
        List<AuditLog> logs = contentQuery
                .setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        return new PageImpl<>(logs, pageable, total);
    }
}
