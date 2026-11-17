package com.sb24.asanzeman.shared.model;

import java.util.Optional;

/**
 * Iranian national code (کد ملی), D30. Validation is always on:
 * Persian/Arabic digits are normalised, exactly 10 digits are required (no zero padding),
 * all-same-digit codes are rejected, and the mod-11 check digit must match.
 */
public record NationalCode(String value) {

    private static final String PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹";
    private static final String ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩";

    public NationalCode {
        if (!isValid(value)) {
            throw new IllegalArgumentException("invalid national code");
        }
    }

    /** Parses user input; returns empty when the normalised text is not a valid national code. */
    public static Optional<NationalCode> parse(String raw) {
        String normalised = normalise(raw);
        return isValid(normalised) ? Optional.of(new NationalCode(normalised)) : Optional.empty();
    }

    /** Converts Persian and Arabic-Indic digits to ASCII and trims surrounding whitespace. */
    public static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        var sb = new StringBuilder(raw.trim().length());
        for (char c : raw.trim().toCharArray()) {
            int p = PERSIAN_DIGITS.indexOf(c);
            int a = ARABIC_DIGITS.indexOf(c);
            sb.append(p >= 0 ? (char) ('0' + p) : a >= 0 ? (char) ('0' + a) : c);
        }
        return sb.toString();
    }

    /** The rule from D30, applied to already-normalised text. */
    public static boolean isValid(String code) {
        if (code == null || code.length() != 10) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (code.charAt(i) < '0' || code.charAt(i) > '9') {
                return false;
            }
        }
        if (code.chars().distinct().count() == 1) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (code.charAt(i) - '0') * (10 - i);
        }
        int remainder = sum % 11;
        int check = code.charAt(9) - '0';
        return remainder < 2 ? check == remainder : check == 11 - remainder;
    }

    @Override
    public String toString() {
        return value;
    }
}
