package com.fintech.fraud.service;

import com.fintech.common.event.TransactionEvent;
import com.fintech.fraud.entity.FraudCheckResult;
import com.fintech.fraud.entity.FraudRule;
import com.fintech.fraud.repository.BlacklistRepository;
import com.fintech.fraud.repository.FraudCheckResultRepository;
import com.fintech.fraud.repository.FraudRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FraudCheckService {

    private final FraudRuleRepository fraudRuleRepository;
    private final FraudCheckResultRepository fraudCheckResultRepository;
    private final BlacklistRepository blacklistRepository;
    private final StringRedisTemplate redisTemplate;

    private static final short SUSPICIOUS_THRESHOLD = 40;
    private static final short BLOCK_THRESHOLD = 70;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Istanbul");
    private static final DefaultRedisScript<Long> DAILY_AMOUNT_SCRIPT = new DefaultRedisScript<>("""
            local created = redis.call('SET', KEYS[1], '1', 'NX', 'EX', ARGV[2])
            if created then
                local total = redis.call('INCRBY', KEYS[2], ARGV[1])
                redis.call('EXPIRE', KEYS[2], ARGV[2])
                return total
            end
            local existing = redis.call('GET', KEYS[2])
            if existing then return tonumber(existing) end
            return 0
            """, Long.class);

    /**
     * Tüm fraud kurallarını çalıştır ve sonuç döndür.
     */
    @Transactional
    public FraudCheckResult performFraudCheck(TransactionEvent event) {
        UUID transactionId = UUID.fromString(event.getTransactionId());
        Optional<FraudCheckResult> existing = fraudCheckResultRepository.findByTransactionId(transactionId);
        if (existing.isPresent()) {
            log.info("Fraud sonucu daha önce hesaplanmış - txId: {}", event.getTransactionId());
            return existing.get();
        }

        long startTime = System.currentTimeMillis();
        short totalScore = 0;
        List<Map<String, Object>> matchedRulesList = new ArrayList<>();

        // 1. Kara liste kontrolü
        if (checkBlacklist(event)) {
            totalScore = 100;
            matchedRulesList.add(Map.of(
                    "rule", "BLACKLIST",
                    "message", "Hesap kara listede",
                    "score", 100
            ));
        } else {
            // 2. Aktif kuralları yükle ve uygula
            List<FraudRule> activeRules = fraudRuleRepository.findByIsActiveTrue();
            BigDecimal dailyAmount = trackDailyAmount(event);

            for (FraudRule rule : activeRules) {
                short ruleScore = evaluateRule(rule, event, dailyAmount);
                if (ruleScore > 0) {
                    totalScore = (short) Math.min(totalScore + ruleScore, 100);
                    matchedRulesList.add(Map.of(
                            "ruleId", rule.getId(),
                            "ruleName", rule.getRuleName(),
                            "ruleType", rule.getRuleType(),
                            "score", ruleScore,
                            "description", rule.getDescription() != null ? rule.getDescription() : ""
                    ));
                }
            }

            // 3. Velocity check (Redis ile hız kontrolü)
            short velocityScore = checkVelocity(event);
            if (velocityScore > 0) {
                totalScore = (short) Math.min(totalScore + velocityScore, 100);
                matchedRulesList.add(Map.of(
                        "rule", "VELOCITY_REDIS",
                        "message", "Kısa sürede çok fazla işlem",
                        "score", velocityScore
                ));
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        boolean isSuspicious = totalScore >= SUSPICIOUS_THRESHOLD;
        boolean isBlocked = totalScore >= BLOCK_THRESHOLD;

        // Sonucu kaydet
        Map<String, Object> matchedRulesMap = new HashMap<>();
        matchedRulesMap.put("rules", matchedRulesList);
        matchedRulesMap.put("totalScore", (int) totalScore);

        FraudCheckResult result = FraudCheckResult.builder()
                .transactionId(transactionId)
                .totalRiskScore(totalScore)
                .isSuspicious(isSuspicious)
                .isBlocked(isBlocked)
                .matchedRules(matchedRulesMap)
                .checkDurationMs((int) duration)
                .build();

        result = fraudCheckResultRepository.save(result);

        log.info("Fraud kontrolü tamamlandı - txId: {}, skor: {}, suspicious: {}, blocked: {}, süre: {}ms, eşleşen kural: {}",
                event.getTransactionId(), totalScore, isSuspicious, isBlocked, duration, matchedRulesList.size());

        return result;
    }

    /**
     * Kara liste kontrolü
     */
    private boolean checkBlacklist(TransactionEvent event) {
        if (event.getSourceAccountId() != null) {
            boolean blocked = blacklistRepository.existsByEntityTypeAndEntityValueAndIsActiveTrue(
                    "ACCOUNT", String.valueOf(event.getSourceAccountId()));
            if (blocked) {
                log.warn("Kara liste eşleşmesi! accountId: {}", event.getSourceAccountId());
                return true;
            }
        }
        if (event.getUserId() != null) {
            boolean blocked = blacklistRepository.existsByEntityTypeAndEntityValueAndIsActiveTrue(
                    "USER", String.valueOf(event.getUserId()));
            if (blocked) {
                log.warn("Kara liste eşleşmesi! userId: {}", event.getUserId());
                return true;
            }
        }
        return false;
    }

    /**
     * Kural motoru - her kuralı değerlendir
     */
    private short evaluateRule(FraudRule rule, TransactionEvent event, BigDecimal dailyAmount) {
        Map<String, Object> condition = rule.getConditionJson();

        return switch (rule.getRuleType()) {
            case "AMOUNT" -> evaluateAmountRule(condition, event, dailyAmount, rule.getRiskWeight());
            case "PATTERN" -> evaluatePatternRule(condition, event, rule.getRiskWeight());
            default -> 0;
        };
    }

    /**
     * Tutar bazlı kural kontrolü
     */
    private short evaluateAmountRule(
            Map<String, Object> condition, TransactionEvent event, BigDecimal dailyAmount, short weight) {
        if (condition.containsKey("max_amount")) {
            double maxAmount = ((Number) condition.get("max_amount")).doubleValue();
            if (event.getAmount().compareTo(BigDecimal.valueOf(maxAmount)) > 0) {
                log.debug("AMOUNT kuralı tetiklendi: {} > {}", event.getAmount(), maxAmount);
                return weight;
            }
        }
        if (condition.containsKey("daily_max_amount")) {
            BigDecimal dailyMax = new BigDecimal(condition.get("daily_max_amount").toString());
            if (dailyAmount.compareTo(dailyMax) > 0) {
                log.debug("DAILY_LIMIT kuralı tetiklendi: günlük toplam {} > {}", dailyAmount, dailyMax);
                return weight;
            }
        }
        return 0;
    }

    /**
     * Desen bazlı kural kontrolü (gece işlemi vs.)
     */
    private short evaluatePatternRule(Map<String, Object> condition, TransactionEvent event, short weight) {
        if (condition.containsKey("start_hour") && condition.containsKey("end_hour")) {
            int startHour = ((Number) condition.get("start_hour")).intValue();
            int endHour = ((Number) condition.get("end_hour")).intValue();
            double minAmount = condition.containsKey("min_amount")
                    ? ((Number) condition.get("min_amount")).doubleValue() : 0;

            LocalTime now = LocalTime.now(ZoneId.of("Europe/Istanbul"));
            int currentHour = now.getHour();

            if (currentHour >= startHour && currentHour < endHour
                    && event.getAmount().compareTo(BigDecimal.valueOf(minAmount)) > 0) {
                log.debug("PATTERN kuralı tetiklendi: saat {} ({}-{} arası), tutar: {}",
                        currentHour, startHour, endHour, event.getAmount());
                return weight;
            }
        }
        return 0;
    }

    /**
     * Redis ile velocity check (hız kontrolü)
     * Son 5 dakikada aynı hesaptan kaç işlem yapıldı?
     */
    private short checkVelocity(TransactionEvent event) {
        if (event.getSourceAccountId() == null) return 0;

        String key = "fraud:velocity:" + event.getSourceAccountId();

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                // İlk kez oluşturuldu, TTL ayarla (5 dakika)
                redisTemplate.expire(key, Duration.ofMinutes(5));
            }

            if (count != null && count > 3) {
                log.warn("Velocity limiti aşıldı! accountId: {}, son 5dk işlem: {}", event.getSourceAccountId(), count);
                return 25;
            }
        } catch (Exception e) {
            log.error("Redis velocity check hatası: {}", e.getMessage());
        }

        return 0;
    }

    /**
     * Aynı event yeniden teslim edilse bile günlük toplama yalnızca bir kez ekler.
     * Tutarlar Redis'te floating point yerine kuruş/cent olarak atomik INCRBY ile tutulur.
     */
    private BigDecimal trackDailyAmount(TransactionEvent event) {
        Long accountId = event.getSourceAccountId() != null
                ? event.getSourceAccountId() : event.getTargetAccountId();
        if (accountId == null || event.getAmount() == null) {
            return event.getAmount() == null ? BigDecimal.ZERO : event.getAmount();
        }

        try {
            long minorUnits = event.getAmount().movePointRight(2).longValueExact();
            LocalDate day = LocalDate.now(BUSINESS_ZONE);
            ZonedDateTime now = ZonedDateTime.now(BUSINESS_ZONE);
            long ttlSeconds = Math.max(60,
                    Duration.between(now, day.plusDays(1).atStartOfDay(BUSINESS_ZONE).plusHours(1)).toSeconds());
            String namespace = accountId + ":" + event.getCurrency() + ":" + day;
            Long totalMinor = redisTemplate.execute(
                    DAILY_AMOUNT_SCRIPT,
                    List.of(
                            "fraud:daily:seen:" + namespace + ":" + event.getTransactionId(),
                            "fraud:daily:amount:" + namespace),
                    String.valueOf(minorUnits),
                    String.valueOf(ttlSeconds));
            return BigDecimal.valueOf(totalMinor == null ? minorUnits : totalMinor, 2);
        } catch (Exception exception) {
            log.error("Redis günlük tutar kontrolü hatası; mevcut işlem tutarıyla devam ediliyor: {}",
                    exception.getMessage());
            return event.getAmount();
        }
    }
}
