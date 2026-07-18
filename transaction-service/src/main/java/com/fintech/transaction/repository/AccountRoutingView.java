package com.fintech.transaction.repository;

public interface AccountRoutingView {
    Long getId();

    Long getUserId();

    String getAccountNumber();

    String getCurrency();

    String getStatus();
}
