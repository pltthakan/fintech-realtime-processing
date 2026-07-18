package com.fintech.account.service;

import com.fintech.account.entity.Account;
import com.fintech.account.repository.AccountRepository;
import com.fintech.account.repository.FundReservationRepository;
import com.fintech.common.enums.AccountType;
import com.fintech.common.enums.Currency;
import com.fintech.common.util.IbanUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private FundReservationRepository fundReservationRepository;
    @Mock
    private LedgerService ledgerService;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, fundReservationRepository, ledgerService);
    }

    @Test
    void newlyOpenedAccountAlwaysStartsAtZero() {
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Account account = service.createAccount(
                7L, "Güvenli Hesap", AccountType.CHECKING, Currency.TRY);

        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getDailySpent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getDailyLimit()).isEqualByComparingTo("50000.00");
        assertThat(IbanUtils.isValidTurkishIban(account.getAccountNumber())).isTrue();
    }
}
