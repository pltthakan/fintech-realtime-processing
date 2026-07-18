package com.fintech.common.util;

import java.util.Locale;

public final class IbanUtils {

    private IbanUtils() {
    }

    public static String normalize(String iban) {
        return iban == null ? null : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValidTurkishIban(String iban) {
        String normalized = normalize(iban);
        if (normalized == null || !normalized.matches("TR\\d{24}")) {
            return false;
        }

        String rearranged = normalized.substring(4) + normalized.substring(0, 4);
        return mod97(rearranged) == 1;
    }

    /** 22 haneli Türkiye BBAN değerinden ISO 13616 MOD-97 kontrollü IBAN üretir. */
    public static String createTurkishIban(String bban) {
        if (bban == null || !bban.matches("\\d{22}")) {
            throw new IllegalArgumentException("Türkiye BBAN değeri 22 haneli olmalıdır");
        }
        int checkDigits = 98 - mod97(bban + "292700"); // T=29, R=27, geçici kontrol hanesi=00
        return "TR" + String.format(Locale.ROOT, "%02d", checkDigits) + bban;
    }

    private static int mod97(String value) {
        int remainder = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            String numeric = Character.isLetter(character)
                    ? Integer.toString(character - 'A' + 10)
                    : Character.toString(character);
            for (int digitIndex = 0; digitIndex < numeric.length(); digitIndex++) {
                remainder = (remainder * 10 + numeric.charAt(digitIndex) - '0') % 97;
            }
        }
        return remainder;
    }

    public static String bankCode(String iban) {
        String normalized = normalize(iban);
        if (normalized == null || normalized.length() < 9) {
            return null;
        }
        return normalized.substring(4, 9);
    }

    public static String mask(String iban) {
        String normalized = normalize(iban);
        if (normalized == null || normalized.length() < 8) {
            return "****";
        }
        return normalized.substring(0, 4) + " **** **** **** **** "
                + normalized.substring(normalized.length() - 4);
    }
}
