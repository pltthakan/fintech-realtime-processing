package com.fintech.fraud.repository;

import com.fintech.fraud.entity.FraudRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FraudRuleRepository extends JpaRepository<FraudRule, Long> {

    List<FraudRule> findByIsActiveTrue();

    List<FraudRule> findByRuleTypeAndIsActiveTrue(String ruleType);
}
