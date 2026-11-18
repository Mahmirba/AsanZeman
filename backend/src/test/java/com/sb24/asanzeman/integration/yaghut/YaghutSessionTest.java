package com.sb24.asanzeman.integration.yaghut;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class YaghutSessionTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T10:00:00Z"));

    @Test
    void isValidUntilItsEndTime() {
        var session = new YaghutSession("abc", Instant.parse("2026-11-17T11:00:00Z"));

        assertThat(session.expired(clock)).isFalse();
        clock.advance(Duration.ofMinutes(59));
        assertThat(session.expired(clock)).isFalse();
        clock.advance(Duration.ofMinutes(1));
        assertThat(session.expired(clock)).isTrue();
    }

    @Test
    void withoutAnEndTimeItNeverExpiresByItself() {
        assertThat(new YaghutSession("abc", null).expired(clock)).isFalse();
    }

    @Test
    void faultsKnowTheirYaghutNames() {
        assertThat(YaghutException.Fault.from("InvalidSessionWSException"))
                .isEqualTo(YaghutException.Fault.INVALID_SESSION);
        assertThat(YaghutException.Fault.from("ServiceAccessIsDeniedWSException").wireName())
                .isEqualTo("ServiceAccessIsDeniedWSException");
        assertThat(YaghutException.Fault.from("WebserviceException")).isNull();
        assertThat(YaghutException.Fault.from(null)).isNull();
        assertThat(new YaghutException(YaghutException.Fault.INVALID_CUSTOMER, "no such customer"))
                .hasMessage("InvalidCustomerWSException: no such customer")
                .extracting(YaghutException::fault).isEqualTo(YaghutException.Fault.INVALID_CUSTOMER);
    }

    @Test
    void aBranchPageCopiesItsList() {
        var list = new java.util.ArrayList<>(List.of(
                new YaghutBranchPage.Branch("1", "n", "c", "a", "t", "f", "z")));
        var page = new YaghutBranchPage(1, list);
        list.clear();

        assertThat(page.branches()).hasSize(1);
        assertThat(page.totalRecord()).isEqualTo(1);
    }
}
