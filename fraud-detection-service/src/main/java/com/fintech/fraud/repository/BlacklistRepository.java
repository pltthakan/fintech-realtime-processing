package com.fintech.fraud.repository;

import com.fintech.fraud.entity.Blacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BlacklistRepository extends JpaRepository<Blacklist, Long> {

    List<Blacklist> findByEntityTypeAndEntityValueAndIsActiveTrue(String entityType, String entityValue);

    boolean existsByEntityTypeAndEntityValueAndIsActiveTrue(String entityType, String entityValue);
}
