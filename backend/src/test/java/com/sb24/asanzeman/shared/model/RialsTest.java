package com.sb24.asanzeman.shared.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RialsTest {

    @Test
    @DisplayName("D18: fractions always round up, never down")
    void roundsUp() {
        assertThat(Rials.roundUp(new BigDecimal("100.0001"))).isEqualTo(Rials.of(101));
        assertThat(Rials.roundUp(new BigDecimal("100.0000"))).isEqualTo(Rials.of(100));
        assertThat(Rials.roundUp(new BigDecimal("0.1"))).isEqualTo(Rials.of(1));
    }

    @Test
    @DisplayName("D14 worked example: 1,000,000,000 with 20 % cash pledges 800,000,000 cheap or 840,000,000 expensive")
    void srsWorkedExample() {
        Rials amount = Rials.of(1_000_000_000L);
        Rials cash = amount.percent(20);
        Rials rest = amount.minus(cash);
        assertThat(cash).isEqualTo(Rials.of(200_000_000L));
        assertThat(rest.times(new BigDecimal("1.00"))).isEqualTo(Rials.of(800_000_000L));
        assertThat(rest.times(new BigDecimal("1.05"))).isEqualTo(Rials.of(840_000_000L));
    }

    @Test
    void arithmeticAndComparison() {
        assertThat(Rials.of(5).plus(Rials.of(7))).isEqualTo(Rials.of(12));
        assertThat(Rials.of(7).minus(Rials.of(5))).isEqualTo(Rials.of(2));
        assertThat(Rials.of(7).isAtLeast(Rials.of(7))).isTrue();
        assertThat(Rials.of(6).isAtLeast(Rials.of(7))).isFalse();
        assertThat(Rials.of(6)).isLessThan(Rials.of(7));
        assertThat(Rials.ZERO.isZero()).isTrue();
        assertThat(Rials.of(1).isZero()).isFalse();
        assertThat(Rials.of(3).toBigDecimal()).isEqualByComparingTo("3");
        assertThat(Rials.of(3)).hasToString("3 IRR");
        assertThat(Rials.of(999).percent(33)).isEqualTo(Rials.of(330)); // 329.67 -> 330
    }

    @Test
    void rejectsNegativeAndOverflow() {
        assertThatThrownBy(() -> Rials.of(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Rials.of(5).minus(Rials.of(6))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Rials.of(Long.MAX_VALUE).plus(Rials.of(1))).isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> Rials.roundUp(new BigDecimal("1e30"))).isInstanceOf(ArithmeticException.class);
    }
}
