package com.sb24.asanzeman.request.domain.model;

import java.math.BigDecimal;

/**
 * A deposit of the customer as the bank reports it (Yaghut {@code deposit}, 5.4), before any rule has looked at it.
 * Every field may be {@code null}; a deposit missing what a rule needs is simply not eligible.
 *
 * @param group            Yaghut's deposit group, such as {@code SHORT_ACCOUNT}
 * @param typeCode         Yaghut's numeric deposit type
 * @param owner            individual or joint ({@code depositOwnerTypeWS})
 * @param signature        the customer's right to sign ({@code OWNER_OF_DEPOSIT_AND_SIGNATURE} for an owner who signs)
 * @param status           {@code OPEN}, or one of the closed and blocked states
 * @param currency         the deposit's currency
 * @param personality      {@code ACTUAL} for a natural person
 * @param availableBalance the withdrawable balance (D16: read once, never saved)
 */
public record CustomerDeposit(String number, String group, Long typeCode, String owner, String signature,
        String status, String currency, String personality, BigDecimal availableBalance) {
}
