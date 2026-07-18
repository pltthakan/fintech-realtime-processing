package com.fintech.paymentrail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(scanBasePackages = {"com.fintech.paymentrail", "com.fintech.common.exception"})
public class PaymentRailServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentRailServiceApplication.class, args);
    }
}
