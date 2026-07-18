package com.fintech.account.repository;

import com.fintech.account.entity.Account;
import com.fintech.common.enums.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /** Pessimistic lock ile hesap getir (bakiye güncelleme için) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);

    /** Karşılıklı transferlerde deadlock oluşmaması için hesapları sabit sırada kilitler. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id")
    List<Account> findAllByIdWithLock(@Param("ids") Collection<Long> ids);

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByUserId(Long userId);

    List<Account> findByUserIdAndStatus(Long userId, AccountStatus status);

    @Query(value = """
            SELECT a.id AS accountId,
                   a.user_id AS userId,
                   a.account_number AS iban,
                   a.currency AS currency,
                   a.status AS status,
                   COALESCE(NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), u.username)
                       AS beneficiaryName
            FROM account_service.accounts a
            JOIN user_service.users u ON u.id = a.user_id
            WHERE a.account_number = :iban
            """, nativeQuery = true)
    Optional<InternalBeneficiaryView> resolveInternalBeneficiary(@Param("iban") String iban);
}
