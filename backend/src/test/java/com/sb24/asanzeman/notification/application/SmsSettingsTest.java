package com.sb24.asanzeman.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SmsSettingsTest {

    private static final SmsSettings SETTINGS = new SmsSettings(20, Duration.ofMinutes(2), Duration.ofSeconds(30),
            Duration.ofHours(1), Duration.ofMinutes(15));

    @Test
    void theRetryDelayDoublesWithEveryAttempt() {
        assertThat(SETTINGS.retryDelay(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(SETTINGS.retryDelay(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(SETTINGS.retryDelay(3)).isEqualTo(Duration.ofSeconds(120));
        assertThat(SETTINGS.retryDelay(4)).isEqualTo(Duration.ofSeconds(240));
    }

    @Test
    void theRetryDelayStopsAtTheMaximum() {
        assertThat(SETTINGS.retryDelay(8)).isEqualTo(Duration.ofHours(1));
        assertThat(SETTINGS.retryDelay(500)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void anAttemptCountBelowOneIsTreatedAsTheFirst() {
        assertThat(SETTINGS.retryDelay(0)).isEqualTo(Duration.ofSeconds(30));
        assertThat(SETTINGS.retryDelay(-3)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void theBatchSizeIsAtLeastOne() {
        assertThatThrownBy(() -> new SmsSettings(0, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
