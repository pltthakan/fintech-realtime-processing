package com.fintech.common.util;

import com.fintech.common.enums.Currency;
import com.fintech.common.enums.TransferRail;

import java.math.BigDecimal;

/**
 * Demo ödeme ağı yönlendirme politikası. Bu eşik mevzuat limiti değil,
 * uygulama içi bir simülasyon sınırıdır.
 */
public final class TransferRoutingPolicy {

    public static final BigDecimal FAST_DEMO_LIMIT = new BigDecimal("20000.00");

    private TransferRoutingPolicy() {
    }

    public static TransferRail selectExternalRail(BigDecimal amount, Currency currency) {
        if (currency != Currency.TRY) {
            throw new IllegalArgumentException("EFT/FAST simülasyonu yalnızca TRY destekler");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Transfer tutarı pozitif olmalıdır");
        }
        return amount.compareTo(FAST_DEMO_LIMIT) <= 0 ? TransferRail.FAST : TransferRail.EFT;
    }
}
