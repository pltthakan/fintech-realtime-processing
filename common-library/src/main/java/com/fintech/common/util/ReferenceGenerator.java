package com.fintech.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * İşlem referans numarası oluşturma.
 * Format: FTK-20260404-XXXXXX (6 haneli rastgele)
 */
public final class ReferenceGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private ReferenceGenerator() {}

    public static String generate() {
        String date = LocalDate.now().format(DATE_FORMAT);
        int random = ThreadLocalRandom.current().nextInt(100000, 999999);
        return String.format("FTK-%s-%d", date, random);
    }

    public static String generateWithPrefix(String prefix) {
        String date = LocalDate.now().format(DATE_FORMAT);
        int random = ThreadLocalRandom.current().nextInt(100000, 999999);
        return String.format("%s-%s-%d", prefix, date, random);
    }
}
