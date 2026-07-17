package com.fintech.fraud.service;

import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransactionType;
import com.fintech.common.event.TransactionEvent;
import com.fintech.fraud.entity.FraudCheckResult;
import com.fintech.fraud.entity.FraudRule;
import com.fintech.fraud.repository.BlacklistRepository;
import com.fintech.fraud.repository.FraudCheckResultRepository;
import com.fintech.fraud.repository.FraudRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudCheckServiceTest {

    @Mock
    private FraudRuleRepository fraudRuleRepository;
    @Mock
    private FraudCheckResultRepository fraudCheckResultRepository;
    @Mock
    private BlacklistRepository blacklistRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private FraudCheckService service;

    @BeforeEach
    void setUp() {
        service = new FraudCheckService(
                fraudRuleRepository, fraudCheckResultRepository, blacklistRepository, redisTemplate);
    }

    @Test
    void dailyAmountRuleUsesIdempotentRedisAggregateInsteadOfSingleTransactionAmount() {
        TransactionEvent event = TransactionEvent.builder()
                .transactionId(UUID.randomUUID().toString())
                .sourceAccountId(10L)
                .amount(new BigDecimal("60.00"))
                .currency(Currency.TRY)
                .type(TransactionType.PAYMENT)
                .userId(7L)
                .build();
        FraudRule dailyRule = FraudRule.builder()
                .id(1L)
                .ruleName("DAILY_AMOUNT")
                .ruleType("AMOUNT")
                .conditionJson(Map.of("daily_max_amount", 100))
                .riskWeight((short) 40)
                .isActive(true)
                .build();

        when(blacklistRepository.existsByEntityTypeAndEntityValueAndIsActiveTrue(anyString(), anyString()))
                .thenReturn(false);
        when(fraudRuleRepository.findByIsActiveTrue()).thenReturn(List.of(dailyRule));
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                anyList(), anyString(), anyString()))
                .thenReturn(12000L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(fraudCheckResultRepository.save(any(FraudCheckResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FraudCheckResult result = service.performFraudCheck(event);

        assertThat(result.getTotalRiskScore()).isEqualTo((short) 40);
        assertThat(result.getIsSuspicious()).isTrue();
        assertThat(result.getIsBlocked()).isFalse();
        assertThat(result.getMatchedRules().toString()).contains("DAILY_AMOUNT");
    }
}
