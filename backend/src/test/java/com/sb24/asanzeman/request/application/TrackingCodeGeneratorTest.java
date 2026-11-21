package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.port.out.TrackingCounterPort;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class TrackingCodeGeneratorTest {

    @Mock
    private TrackingCounterPort counters;

    @Test
    void usesTheJalaliDayAndTheNextCounter() {
        // 2026-11-21 10:00 UTC is 13:30 in Tehran: 1405/08/30
        when(counters.next("0123", "0042", "050830")).thenReturn(3);

        var code = new TrackingCodeGenerator(counters, new MutableClock(Instant.parse("2026-11-21T10:00:00Z")))
                .next("0123", "0042");

        assertThat(code.value()).isEqualTo("01230042" + "050830" + "0003");
    }

    @Test
    void justAfterMidnightInTehranIsAlreadyTheNextDay() {
        // 2026-11-21 20:45 UTC is 00:15 on 22 November in Tehran: 1405/09/01
        when(counters.next("0123", "0042", "050901")).thenReturn(1);

        var code = new TrackingCodeGenerator(counters, new MutableClock(Instant.parse("2026-11-21T20:45:00Z")))
                .next("0123", "0042");

        assertThat(code.value()).isEqualTo("01230042" + "050901" + "0001");
    }

    @Test
    void itJoinsTheRegistrationTransaction() throws Exception {
        var method = TrackingCodeGenerator.class.getMethod("next", String.class, String.class);

        assertThat(method.getAnnotation(Transactional.class).propagation()).isEqualTo(Propagation.MANDATORY);
    }
}
