package com.fintech.common.audit;

/**
 * Kullanıcı tarafından başlatılan, denetlenmesi gereken API eylemleri.
 */
public enum AuditAction {
    ACCOUNT_VIEWED,
    ACCOUNT_LIST_VIEWED,
    ACCOUNT_CREATED,
    TRANSACTION_VIEWED,
    TRANSACTION_LIST_VIEWED,
    TRANSACTION_HISTORY_VIEWED,
    TRANSACTION_CREATED,
    AUDIT_LOG_VIEWED
}
