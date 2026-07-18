package com.fintech.common.event;

/**
 * Kafka topic isimleri. Tüm servisler bu sabitleri kullanır.
 *
 * Pipeline akışı:
 * transaction-raw → transaction-validated → transaction-checked
 * → transaction-processed → transaction-completed → MongoDB (Kafka Connect)
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /** Transaction Service (A) → buraya yazar */
    public static final String TRANSACTION_RAW = "transaction-raw";

    /** Fraud Service (B) → buradan okur, buraya yazar */
    public static final String TRANSACTION_VALIDATED = "transaction-validated";

    /** Account Service (C) → buradan okur, buraya yazar */
    public static final String TRANSACTION_CHECKED = "transaction-checked";

    /** Notification Service (D) → buradan okur, buraya yazar */
    public static final String TRANSACTION_PROCESSED = "transaction-processed";

    /** Kafka Connect → buradan okur → MongoDB'ye yazar */
    public static final String TRANSACTION_COMPLETED = "transaction-completed";

    /** Account Service dış transfer tutarını rezerve ettikten sonra yayınlar. */
    public static final String FUNDS_RESERVED = "funds-reserved";

    /** Payment Rail Service EFT/FAST mutabakat sonucunu buraya yayınlar. */
    public static final String TRANSFER_RAIL_RESULT = "transfer-rail-result";

    /** Hatalı işlemler için Dead Letter Queue */
    public static final String TRANSACTION_DLQ = "transaction-dlq";
}
