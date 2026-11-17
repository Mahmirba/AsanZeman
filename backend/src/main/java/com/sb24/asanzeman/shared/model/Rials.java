package com.sb24.asanzeman.shared.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Whole-rial amounts (D18). Every intermediate collateral calculation is a {@link BigDecimal};
 * the final figure is rounded <b>up</b> so the bank is never under-secured.
 */
public record Rials(long value) implements Comparable<Rials> {

    public static final Rials ZERO = new Rials(0);

    public Rials {
        if (value < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + value);
        }
    }

    public static Rials of(long value) {
        return new Rials(value);
    }

    /** Rounds up (CEILING) to a whole rial. */
    public static Rials roundUp(BigDecimal exact) {
        return new Rials(exact.setScale(0, RoundingMode.CEILING).longValueExact());
    }

    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(value);
    }

    public Rials plus(Rials other) {
        return new Rials(Math.addExact(value, other.value));
    }

    public Rials minus(Rials other) {
        return new Rials(Math.subtractExact(value, other.value));
    }

    /** {@code this × factor}, rounded up. */
    public Rials times(BigDecimal factor) {
        return roundUp(toBigDecimal().multiply(factor));
    }

    /** {@code this × percent / 100}, rounded up. */
    public Rials percent(int percent) {
        return roundUp(toBigDecimal().multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100)));
    }

    public boolean isZero() {
        return value == 0;
    }

    public boolean isAtLeast(Rials other) {
        return value >= other.value;
    }

    @Override
    public int compareTo(Rials other) {
        return Long.compare(value, other.value);
    }

    @Override
    public String toString() {
        return value + " IRR";
    }
}
