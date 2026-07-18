package com.fintech.account.repository;

public interface InternalBeneficiaryView {
    Long getAccountId();

    Long getUserId();

    String getIban();

    String getCurrency();

    String getStatus();

    String getBeneficiaryName();
}
