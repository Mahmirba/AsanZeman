package com.sb24.asanzeman.request.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.RequestSearchCriteria;
import com.sb24.asanzeman.request.domain.model.RequestType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestFiltersTest {

    private static final UUID AGENCY = UUID.randomUUID();

    private static RequestSearchCriteria of(String code, String number, List<String> types, String tracking,
            String name, String from, String to) {
        return RequestFilters.of(AGENCY, code, number, types, tracking, name, from, to);
    }

    @Test
    void noFiltersIsJustTheAgency() {
        RequestSearchCriteria c = of(null, null, null, null, null, null, null);

        assertThat(c.agencyId()).isEqualTo(AGENCY);
        assertThat(c.customerCode()).isNull();
        assertThat(c.types()).isEmpty();
        assertThat(c.registeredFrom()).isNull();
        assertThat(c.registeredTo()).isNull();
    }

    @Test
    void blankFiltersAreDroppedAndTextIsTrimmed() {
        RequestSearchCriteria c = of(" ", " ", List.of(), " ", " ", " ", " ");

        assertThat(c).isEqualTo(of(null, null, null, null, null, null, null));
        assertThat(of(null, " 555 ", null, " 123 ", " Ali ", null, null)).extracting("customerCif", "trackingCode",
                "name").containsExactly("555", "123", "Ali");
    }

    @Test
    void theNationalCodeIsNormalisedAndChecked() {
        assertThat(of("۰۰۱۲۳۴۵۶۷۹", null, null, null, null, null, null).customerCode()).isEqualTo("0012345679");
        assertThatThrownBy(() -> of("1234567890", null, null, null, null, null, null))
                .isInstanceOf(InvalidRequestDataException.class);
    }

    @Test
    void typesAreCaseInsensitiveAndUnknownOnesAreRefused() {
        assertThat(of(null, null, List.of("issuance", " REVOCATION "), null, null, null, null).types())
                .containsExactlyInAnyOrder(RequestType.ISSUANCE, RequestType.REVOCATION);
        assertThatThrownBy(() -> of(null, null, List.of("nope"), null, null, null, null))
                .isInstanceOf(InvalidRequestDataException.class);
    }

    @Test
    void theJalaliRangeCoversWholeDaysInTehranTime() {
        RequestSearchCriteria c = of(null, null, null, null, null, "1405/08/01", "1405/08/30");

        // Tehran is UTC+3:30 all year
        assertThat(c.registeredFrom()).isEqualTo(Instant.parse("2026-10-22T20:30:00Z"));
        assertThat(c.registeredTo()).isEqualTo(Instant.parse("2026-11-21T20:30:00Z"));
    }

    @Test
    void aSingleDayIsAValidRange() {
        RequestSearchCriteria c = of(null, null, null, null, null, "1405/08/30", "1405/08/30");

        assertThat(c.registeredTo()).isAfter(c.registeredFrom());
    }

    @Test
    void aBadOrBackwardsDateIsRefused() {
        assertThatThrownBy(() -> of(null, null, null, null, null, "2026-11-21", null))
                .isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> of(null, null, null, null, null, null, "1405/13/40"))
                .isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> of(null, null, null, null, null, "1405/08/30", "1405/08/01"))
                .isInstanceOf(InvalidRequestDataException.class);
    }
}
