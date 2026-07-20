package com.fintech.account.service;

import com.fintech.account.client.UserDirectoryClient;
import com.fintech.account.entity.Account;
import com.fintech.account.dto.InternalBeneficiaryResponse;
import com.fintech.account.repository.AccountRepository;
import com.fintech.account.repository.FundReservationRepository;
import com.fintech.common.dto.internal.UserSnapshot;
import com.fintech.common.enums.AccountStatus;
import com.fintech.common.enums.AccountType;
import com.fintech.common.enums.Currency;
import com.fintech.common.util.IbanUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

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
    @Mock
    private UserDirectoryClient userDirectoryClient;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(
                accountRepository, fundReservationRepository, ledgerService, userDirectoryClient);
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

    @Test
    void resolvesBeneficiaryNameThroughUserServiceBoundary() {
        String iban = "TR760006100519786457841328";
        Account account = Account.builder()
                .id(23L)
                .userId(22L)
                .accountNumber(iban)
                .currency(Currency.TRY)
                .status(AccountStatus.ACTIVE)
                .build();
        when(accountRepository.findByAccountNumber(iban)).thenReturn(Optional.of(account));
        when(userDirectoryClient.getUser(22L)).thenReturn(UserSnapshot.builder()
                .userId(22L)
                .username("ayse")
                .displayName("Ayşe Demir")
                .build());

        InternalBeneficiaryResponse response = service.resolveInternalBeneficiary(iban);

        assertThat(response.getAccountId()).isEqualTo(23L);
        assertThat(response.getBeneficiaryName()).isEqualTo("Ayşe Demir");
        assertThat(response.getCurrency()).isEqualTo("TRY");
    }
}
