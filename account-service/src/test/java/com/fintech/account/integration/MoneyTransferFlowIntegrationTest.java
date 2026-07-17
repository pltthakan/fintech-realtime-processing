package com.fintech.account.integration;

import com.fintech.account.entity.Account;
import com.fintech.account.repository.AccountRepository;
import com.fintech.common.enums.AccountStatus;
import com.fintech.common.enums.AccountType;
import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionStatus;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.event.KafkaTopics;
import com.fintech.common.event.TransactionEvent;
import com.fintech.common.util.JsonUtil;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "server.port=0",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "management.health.redis.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.datasource.hikari.maximum-pool-size=5",
        "outbox.publisher.fixed-delay-ms=100",
        "debug=false",
        "logging.level.root=WARN",
        "logging.level.com.fintech.account=INFO",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.springframework.kafka.listener=OFF",
        "logging.level.org.testcontainers=INFO"
})
class MoneyTransferFlowIntegrationTest {

    private static final String ACCOUNT_CONSUMER_GROUP = "account-service-group";
    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(1);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("fintech_test")
            .withUsername("fintech_test")
            .withPassword("fintech_test")
            .withInitScript("sql/account-test-schema.sql");

    @Container
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @DynamicPropertySource
    static void registerContainerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeAll
    static void createKafkaTopics() throws Exception {
        try (AdminClient admin = adminClient()) {
            createTopic(admin, KafkaTopics.TRANSACTION_VALIDATED);
            createTopic(admin, KafkaTopics.TRANSACTION_CHECKED);
            createTopic(admin, KafkaTopics.TRANSACTION_DLQ);
        }
    }

    @Test
    void duplicateTransferEventMovesMoneyAndPublishesNextEventExactlyOnce() throws Exception {
        long ownerId = 42L;
        Account source = createAccount(ownerId, "1000.00");
        Account target = createAccount(84L, "250.00");
        String transactionId = UUID.randomUUID().toString();

        TransactionEvent event = transferEvent(
                transactionId, ownerId, source.getId(), target.getId(), "125.50");
        String payload = JsonUtil.toJson(event);

        kafkaTemplate.send(KafkaTopics.TRANSACTION_VALIDATED, transactionId, payload)
                .get(10, TimeUnit.SECONDS);
        RecordMetadata duplicateRecord = kafkaTemplate
                .send(KafkaTopics.TRANSACTION_VALIDATED, transactionId, payload)
                .get(10, TimeUnit.SECONDS)
                .getRecordMetadata();

        awaitConsumerOffset(duplicateRecord);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo("874.50");
            assertThat(accountRepository.findById(target.getId()).orElseThrow().getBalance())
                    .isEqualByComparingTo("375.50");
            assertThat(countProcessedEvents(transactionId)).isEqualTo(1);
            assertThat(countOutboxEvents(transactionId)).isEqualTo(1);
            assertThat(outboxStatus(transactionId)).isEqualTo("PUBLISHED");
        });

        TransactionEvent checkedEvent = consumeEvent(
                KafkaTopics.TRANSACTION_CHECKED, transactionId, Duration.ofSeconds(15));
        assertThat(checkedEvent.getStatus()).isEqualTo(TransactionStatus.PROCESSED);
        assertThat(checkedEvent.getAmount()).isEqualByComparingTo("125.50");
    }

    @Test
    void insufficientBalanceRollsBackMoneyAndInboxThenRoutesEventToDlq() throws Exception {
        long ownerId = 100L;
        Account source = createAccount(ownerId, "50.00");
        Account target = createAccount(200L, "20.00");
        String transactionId = UUID.randomUUID().toString();

        TransactionEvent event = transferEvent(
                transactionId, ownerId, source.getId(), target.getId(), "100.00");
        kafkaTemplate.send(
                        KafkaTopics.TRANSACTION_VALIDATED,
                        transactionId,
                        JsonUtil.toJson(event))
                .get(10, TimeUnit.SECONDS);

        TransactionEvent deadLetterEvent = consumeEvent(
                KafkaTopics.TRANSACTION_DLQ, transactionId, Duration.ofSeconds(20));
        assertThat(deadLetterEvent.getTransactionId()).isEqualTo(transactionId);

        assertThat(accountRepository.findById(source.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("50.00");
        assertThat(accountRepository.findById(target.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("20.00");
        assertThat(countProcessedEvents(transactionId)).isZero();
        assertThat(countOutboxEvents(transactionId)).isZero();
    }

    private Account createAccount(Long userId, String balance) {
        long sequence = ACCOUNT_SEQUENCE.getAndIncrement();
        return accountRepository.saveAndFlush(Account.builder()
                .userId(userId)
                .accountNumber(String.format("TR%024d", sequence))
                .accountName("Integration Test Account " + sequence)
                .accountType(AccountType.CHECKING)
                .currency(Currency.TRY)
                .balance(new BigDecimal(balance))
                .dailyLimit(new BigDecimal("50000.00"))
                .status(AccountStatus.ACTIVE)
                .build());
    }

    private TransactionEvent transferEvent(
            String transactionId,
            Long ownerId,
            Long sourceAccountId,
            Long targetAccountId,
            String amount) {
        return TransactionEvent.builder()
                .transactionId(transactionId)
                .sourceAccountId(sourceAccountId)
                .targetAccountId(targetAccountId)
                .userId(ownerId)
                .username("integration-test")
                .amount(new BigDecimal(amount))
                .currency(Currency.TRY)
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.CHECKED)
                .checkedTimestamp(Instant.now())
                .build();
    }

    private void awaitConsumerOffset(RecordMetadata record) {
        TopicPartition partition = new TopicPartition(record.topic(), record.partition());

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            try (AdminClient admin = adminClient()) {
                Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets = admin
                        .listConsumerGroupOffsets(ACCOUNT_CONSUMER_GROUP)
                        .partitionsToOffsetAndMetadata()
                        .get(5, TimeUnit.SECONDS);
                assertThat(offsets).containsKey(partition);
                assertThat(offsets.get(partition).offset()).isGreaterThan(record.offset());
            }
        });
    }

    private TransactionEvent consumeEvent(String topic, String transactionId, Duration timeout) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "integration-test-" + UUID.randomUUID(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
        );

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plus(timeout);

            while (Instant.now().isBefore(deadline)) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                for (ConsumerRecord<String, String> record : records) {
                    TransactionEvent event = JsonUtil.fromJson(record.value(), TransactionEvent.class);
                    if (transactionId.equals(event.getTransactionId())) {
                        return event;
                    }
                }
            }
        }

        throw new AssertionError("Kafka event was not received from " + topic + " for " + transactionId);
    }

    private int countProcessedEvents(String transactionId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_service.processed_events WHERE event_id = ?",
                Integer.class,
                transactionId);
    }

    private int countOutboxEvents(String transactionId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account_service.outbox_events WHERE aggregate_id = ?",
                Integer.class,
                transactionId);
    }

    private String outboxStatus(String transactionId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM account_service.outbox_events WHERE aggregate_id = ?",
                String.class,
                transactionId);
    }

    private static AdminClient adminClient() {
        return AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()));
    }

    private static void createTopic(AdminClient admin, String topic) throws Exception {
        try {
            admin.createTopics(List.of(new NewTopic(topic, 3, (short) 1)))
                    .all()
                    .get(10, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            if (!(exception.getCause() instanceof TopicExistsException)) {
                throw exception;
            }
        }
    }
}
