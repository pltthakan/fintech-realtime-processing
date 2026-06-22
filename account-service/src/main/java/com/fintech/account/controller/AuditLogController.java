package com.fintech.account.controller;

import com.fintech.common.audit.AuditAction;
import com.fintech.common.audit.AuditLog;
import com.fintech.common.audit.AuditLogEntry;
import com.fintech.common.audit.AuditLogService;
import com.fintech.common.dto.response.ApiResponse;
import com.fintech.common.dto.response.PagedResponse;
import com.fintech.common.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Merkezi audit kayıtları yalnızca yönetici tarafından görüntülenebilir.
 */
@Validated
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<AuditLog>>> getRecentAuditLogs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Name") String username,
            @RequestHeader("X-User-Role") String role,
            HttpServletRequest request) {
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException();
        }

        Page<AuditLog> auditLogs = auditLogService.getRecent(PageRequest.of(page, size));
        auditLogService.record(AuditLogEntry.builder()
                .actorUserId(userId)
                .actorUsername(username)
                .actorRole(role)
                .action(AuditAction.AUDIT_LOG_VIEWED)
                .resourceType("AUDIT_LOG")
                .resourceId("recent")
                .serviceName("account-service")
                .httpMethod("GET")
                .clientIp(resolveClientIp(request))
                .details("page=" + page + ",size=" + size)
                .build());

        PagedResponse<AuditLog> response = PagedResponse.<AuditLog>builder()
                .content(auditLogs.getContent())
                .page(auditLogs.getNumber())
                .size(auditLogs.getSize())
                .totalElements(auditLogs.getTotalElements())
                .totalPages(auditLogs.getTotalPages())
                .first(auditLogs.isFirst())
                .last(auditLogs.isLast())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String clientIp = request.getHeader("X-Client-Ip");
        if (clientIp != null && !clientIp.isBlank()) {
            return clientIp;
        }
        return request.getRemoteAddr();
    }
}
