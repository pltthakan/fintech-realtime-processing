package com.fintech.common.enums;

public enum TransactionStatus {
    PENDING,          // İşlem oluşturuldu, henüz pipeline'a girmedi
    VALIDATED,        // Transaction Service (A) doğruladı
    FRAUD_CHECK,      // Fraud Service (B) kontrol ediyor
    CHECKED,          // Fraud kontrolden geçti
    BLOCKED,          // Fraud tarafından engellendi
    PROCESSING,       // Account Service (C) bakiye güncelliyor
    PROCESSED,        // Bakiye güncellendi
    COMPLETED,        // Tüm pipeline tamamlandı
    FAILED,           // İşlem başarısız
    CANCELLED         // İşlem iptal edildi
}
