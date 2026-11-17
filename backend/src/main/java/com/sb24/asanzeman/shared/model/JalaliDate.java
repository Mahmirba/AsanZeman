package com.sb24.asanzeman.shared.model;

import java.time.LocalDate;

/**
 * Solar Hijri (Jalali) calendar date. Used for the tracking code (D12: YYMMDD), SMS texts and the
 * inquiry request formats. Conversion follows the standard 2820-year-cycle-free algorithm that is
 * exact for the years the bank operates in (1300-1500 SH); verified by round-trip tests.
 */
public record JalaliDate(int year, int month, int day) implements Comparable<JalaliDate> {

    private static final int[] GREGORIAN_DAYS_BEFORE_MONTH = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};

    public JalaliDate {
        if (month < 1 || month > 12 || day < 1 || day > lengthOfMonth(year, month)) {
            throw new IllegalArgumentException("invalid Jalali date " + year + "/" + month + "/" + day);
        }
    }

    public static JalaliDate of(int year, int month, int day) {
        return new JalaliDate(year, month, day);
    }

    public static JalaliDate fromGregorian(LocalDate date) {
        int gy = date.getYear();
        int gm = date.getMonthValue();
        int gd = date.getDayOfMonth();
        int gy2 = gm > 2 ? gy + 1 : gy;
        long days = 355666L + 365L * gy + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400)
                + gd + GREGORIAN_DAYS_BEFORE_MONTH[gm - 1];
        int jy = -1595 + (int) (33 * (days / 12053));
        days %= 12053;
        jy += 4 * (int) (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (int) ((days - 1) / 365);
            days = (days - 1) % 365;
        }
        int jm;
        int jd;
        if (days < 186) {
            jm = 1 + (int) (days / 31);
            jd = 1 + (int) (days % 31);
        } else {
            jm = 7 + (int) ((days - 186) / 30);
            jd = 1 + (int) ((days - 186) % 30);
        }
        return new JalaliDate(jy, jm, jd);
    }

    public LocalDate toGregorian() {
        int jy = year + 1595;
        long days = -355668L + 365L * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4
                + day + (month < 7 ? (month - 1) * 31 : (month - 7) * 30 + 186);
        int gy = 400 * (int) (days / 146097);
        days %= 146097;
        if (days > 36524) {
            gy += 100 * (int) (--days / 36524);
            days %= 36524;
            if (days >= 365) {
                days++;
            }
        }
        gy += 4 * (int) (days / 1461);
        days %= 1461;
        if (days > 365) {
            gy += (int) ((days - 1) / 365);
            days = (days - 1) % 365;
        }
        int gd = (int) days + 1;
        int[] monthLengths = {0, 31, isGregorianLeap(gy) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int gm = 0;
        while (gm < 13 && gd > monthLengths[gm]) {
            gd -= monthLengths[gm];
            gm++;
        }
        return LocalDate.of(gy, gm, gd);
    }

    /** 33-year cycle, exact for 1300-1500 SH and consistent with {@link #fromGregorian}. */
    public static boolean isLeap(int year) {
        return switch (year % 33) {
            case 1, 5, 9, 13, 17, 22, 26, 30 -> true;
            default -> false;
        };
    }

    public static int lengthOfMonth(int year, int month) {
        if (month <= 6) {
            return 31;
        }
        if (month <= 11) {
            return 30;
        }
        return isLeap(year) ? 30 : 29;
    }

    private static boolean isGregorianLeap(int gy) {
        return (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0;
    }

    /** {@code YYMMDD}, the tracking-code segment of D12. */
    public String toYyMmDd() {
        return String.format("%02d%02d%02d", year % 100, month, day);
    }

    /** {@code yyyy/MM/dd}, as the Sepad inquiry expects. */
    public String toSlashed() {
        return String.format("%04d/%02d/%02d", year, month, day);
    }

    /** {@code yyyyMMdd}, as the Sabte Ahval inquiry expects. */
    public String toCompact() {
        return String.format("%04d%02d%02d", year, month, day);
    }

    @Override
    public int compareTo(JalaliDate other) {
        int c = Integer.compare(year, other.year);
        if (c == 0) {
            c = Integer.compare(month, other.month);
        }
        return c == 0 ? Integer.compare(day, other.day) : c;
    }

    @Override
    public String toString() {
        return toSlashed();
    }
}
