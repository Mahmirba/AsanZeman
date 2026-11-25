package com.sb24.asanzeman.request.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.shared.model.DomainException;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;

class AdminDataTest {

    @Test
    void anAgencyIsTrimmedAndAnEmptyMobileIsNone() {
        var data = new AgencyData(" 9001 ", "0042", " Sky Travel ", true, "  ");

        assertThat(data).isEqualTo(new AgencyData("9001", "0042", "Sky Travel", true, null));
        assertThat(new AgencyData("9001", "0042", "Sky", false, "09121234567").mobile()).isEqualTo("09121234567");
    }

    @Test
    void anAgencyNeedsAValidCustomerNumberCodeNameAndMobile() {
        assertInvalid(() -> new AgencyData(null, "0042", "Sky", true, null));
        assertInvalid(() -> new AgencyData("", "0042", "Sky", true, null));
        assertInvalid(() -> new AgencyData("1".repeat(21), "0042", "Sky", true, null));
        assertInvalid(() -> new AgencyData("9001", null, "Sky", true, null));
        assertInvalid(() -> new AgencyData("9001", "42", "Sky", true, null));
        assertInvalid(() -> new AgencyData("9001", "00a2", "Sky", true, null));
        assertInvalid(() -> new AgencyData("9001", "0042", null, true, null));
        assertInvalid(() -> new AgencyData("9001", "0042", " ", true, null));
        assertInvalid(() -> new AgencyData("9001", "0042", "x".repeat(201), true, null));
        assertInvalid(() -> new AgencyData("9001", "0042", "Sky", true, "9121234567"));
        assertInvalid(() -> new AgencyData("9001", "0042", "Sky", true, "0912123456"));
    }

    @Test
    void theLimitsThemselvesAreAccepted() {
        assertThat(new AgencyData("1".repeat(20), "0000", "x".repeat(200), true, null)).isNotNull();
        assertThat(new ReasonData("ISSUANCE", "x".repeat(500), true, 99999)).isNotNull();
        assertThat(new ReasonData("REVOCATION", "a", false, 0)).isNotNull();
    }

    @Test
    void aReasonIsTrimmedAndNeedsAKindATextAndAnOrder() {
        assertThat(new ReasonData("ISSUANCE", "  Incomplete  ", true, 1).text()).isEqualTo("Incomplete");
        assertInvalid(() -> new ReasonData(null, "t", true, 1));
        assertInvalid(() -> new ReasonData("OTHER", "t", true, 1));
        assertInvalid(() -> new ReasonData("ISSUANCE", null, true, 1));
        assertInvalid(() -> new ReasonData("ISSUANCE", "  ", true, 1));
        assertInvalid(() -> new ReasonData("ISSUANCE", "x".repeat(501), true, 1));
        assertInvalid(() -> new ReasonData("ISSUANCE", "t", true, -1));
        assertInvalid(() -> new ReasonData("ISSUANCE", "t", true, 100000));
    }

    @Test
    void theTwoRefusalsHaveTheirOwnStatus() {
        assertThat(new AdminItemNotFoundException().kind()).isEqualTo(DomainException.Kind.NOT_FOUND);
        assertThat(new AgencyDuplicateException().kind()).isEqualTo(DomainException.Kind.CONFLICT);
    }

    private static void assertInvalid(ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(InvalidRequestDataException.class);
    }
}
