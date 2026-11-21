package com.sb24.asanzeman.request.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class LogOnlyRegistrationNotifierTest {

    @Test
    void itOnlyLogsAndNeverFails() {
        assertThatCode(() -> new LogOnlyRegistrationNotifier().customerRegistered("09121234567", "Sky", "123"))
                .doesNotThrowAnyException();
    }
}
