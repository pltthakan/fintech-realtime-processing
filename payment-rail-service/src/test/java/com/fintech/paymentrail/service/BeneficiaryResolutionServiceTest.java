package com.fintech.paymentrail.service;

import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransferRail;
import com.fintech.common.exception.BusinessException;
import com.fintech.paymentrail.dto.BeneficiaryResolveRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BeneficiaryResolutionServiceTest {

    private static final String INTERNAL_IBAN = "TR760006100519786457841328";
    private static final String EXTERNAL_IBAN = "TR190012300000000000000001";

    private MockRestServiceServer accountServer;
    private BeneficiaryResolutionService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        accountServer = MockRestServiceServer.bindTo(builder).build();
        service = new BeneficiaryResolutionService(builder, "http://account-service");
    }

    @Test
    void routesAnotherPlatformUsersAccountAsHavale() {
        accountServer.expect(once(), requestTo(
                        "http://account-service/api/v1/internal/accounts/beneficiaries/" + INTERNAL_IBAN))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": {
                            "accountId": 12,
                            "userId": 4,
                            "iban": "TR760006100519786457841328",
                            "currency": "TRY",
                            "status": "ACTIVE",
                            "beneficiaryName": "Ayşe Demir"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var result = service.resolve(request(INTERNAL_IBAN, "Ayşe Demir", "250.00"), 3L);

        assertThat(result.getRail()).isEqualTo(TransferRail.HAVALE);
        assertThat(result.isInternal()).isTrue();
        assertThat(result.getMaskedIban()).endsWith("1328");
        assertThat(result.getMaskedBeneficiaryName()).isEqualTo("A*** D****");
        accountServer.verify();
    }

    @Test
    void rejectsOwnAccountFromAnotherAccountFlow() {
        accountServer.expect(once(), requestTo(
                        "http://account-service/api/v1/internal/accounts/beneficiaries/" + INTERNAL_IBAN))
                .andRespond(withSuccess("""
                        {
                          "success": true,
                          "data": {
                            "accountId": 12,
                            "userId": 3,
                            "iban": "TR760006100519786457841328",
                            "currency": "TRY",
                            "status": "ACTIVE",
                            "beneficiaryName": "Ahmet Yılmaz"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.resolve(request(INTERNAL_IBAN, "Ahmet Yılmaz", "250.00"), 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Kendi Hesaplarım");
        accountServer.verify();
    }

    @Test
    void routesExternalTryTransfersAcrossFastBoundary() {
        accountServer.expect(once(), requestTo(
                        "http://account-service/api/v1/internal/accounts/beneficiaries/" + EXTERNAL_IBAN))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        var fast = service.resolve(request(EXTERNAL_IBAN, "Dış Alıcı", "20000.00"), 3L);

        assertThat(fast.getRail()).isEqualTo(TransferRail.FAST);
        assertThat(fast.isInternal()).isFalse();
        assertThat(fast.getMaskedIban()).doesNotContain("000000000000000000");
        accountServer.verify();

        RestClient.Builder builder = RestClient.builder();
        accountServer = MockRestServiceServer.bindTo(builder).build();
        service = new BeneficiaryResolutionService(builder, "http://account-service");
        accountServer.expect(once(), requestTo(
                        "http://account-service/api/v1/internal/accounts/beneficiaries/" + EXTERNAL_IBAN))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        var eft = service.resolve(request(EXTERNAL_IBAN, "Dış Alıcı", "20000.01"), 3L);

        assertThat(eft.getRail()).isEqualTo(TransferRail.EFT);
        assertThat(eft.isInternal()).isFalse();
        accountServer.verify();
    }

    private BeneficiaryResolveRequest request(String iban, String name, String amount) {
        BeneficiaryResolveRequest request = new BeneficiaryResolveRequest();
        request.setSourceAccountId(10L);
        request.setIban(iban);
        request.setBeneficiaryName(name);
        request.setAmount(new BigDecimal(amount));
        request.setCurrency(Currency.TRY);
        return request;
    }
}
