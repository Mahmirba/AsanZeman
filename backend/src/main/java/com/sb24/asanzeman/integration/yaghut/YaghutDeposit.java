package com.sb24.asanzeman.integration.yaghut;

import java.math.BigDecimal;

/**
 * One deposit of {@code deposit} (5.4, the bank's service document): its number, group, numeric type, whether it is held
 * alone or jointly, the right to sign, its status, currency, personality and withdrawable balance. Every field may be
 * {@code null}; the caller's rules decide what that means.
 *
 * <p><b>Assumption to confirm in UAT:</b> the document lists these field names but shows no sample answer, so the
 * wrapper around the list and the {@code owner} values are guessed in {@link YaghutClient#deposits}.
 */
public record YaghutDeposit(String number, String group, Long typeCode, String owner, String signature,
        String status, String currency, String personality, BigDecimal availableBalance) {
}
