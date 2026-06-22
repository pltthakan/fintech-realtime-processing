package com.fintech.common.audit;

import lombok.Builder;

/**
 * Bir HTTP eyleminden audit deposuna aktarılacak bağlam.
 */
@Builder
public record AuditLogEntry(
        Long actorUserId,
        String actorUsername,
        String actorRole,
        AuditAction action,
        String resourceType,
        String resourceId,
        String serviceName,
        String httpMethod,
        String clientIp,
        String details
) {
}
