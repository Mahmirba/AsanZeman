package com.sb24.asanzeman.request.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.shared.model.DomainException;
import com.sb24.asanzeman.shared.model.JalaliDate;
import com.sb24.asanzeman.shared.model.Rials;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 21);
    static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    static final UUID AGENCY = UUID.randomUUID();
    static final TrackingCode CODE = TrackingCode.of("0123", "0042", JalaliDate.of(1405, 8, 30), 1);
    // a valid national code (checksum 0)
    static final String NATIONAL_CODE = "0012345679";

    static CustomerSnapshot customer(LocalDate birth, String personality) {
        return new CustomerSnapshot("555", "1000000011", "Ali", "Rezaei", "Ali Rezaei", "Reza", birth, "09121234567",
                "123", "S", "T", personality, "MALE");
    }

    static Companion companion() {
        return new Companion("Sara Rezaei", NATIONAL_CODE, LocalDate.of(2015, 1, 1), "P1234567");
    }

    private static Request register(CustomerSnapshot customer, Rials amount, List<Companion> companions) {
        return Request.register(UUID.randomUUID(), CODE, AGENCY, "0123", amount, LocalDate.of(2027, 1, 20), customer,
                companions, "Sky Travel", NOW, TODAY);
    }

    @Test
    void aValidRegistrationIsAnIssuanceRequestAtTheAgency() {
        Request request = register(customer(LocalDate.of(1990, 5, 17), "ACTUAL"), Rials.of(100_000_000),
                List.of(companion()));

        assertThat(request.type()).isEqualTo(RequestType.ISSUANCE);
        assertThat(request.status()).isEqualTo(RequestStatus.REGISTERED_AT_AGENCY);
        assertThat(request.trackingCode()).isEqualTo(CODE);
        assertThat(request.branchCode()).isEqualTo("0123");
        assertThat(request.companions()).hasSize(1);
        assertThat(request.registeredBy()).isEqualTo("Sky Travel");
        assertThat(request.registeredAt()).isEqualTo(NOW);
    }

    @Test
    void theCompanionListIsCopied() {
        var list = new java.util.ArrayList<>(List.of(companion()));
        Request request = register(customer(LocalDate.of(1990, 5, 17), "ACTUAL"), Rials.of(1), list);

        list.clear();

        assertThat(request.companions()).hasSize(1);
    }

    @Test
    void aCustomerWhoTurns18TodayIsAccepted() {
        assertThat(register(customer(TODAY.minusYears(18), "ACTUAL"), Rials.of(1), List.of()).status())
                .isEqualTo(RequestStatus.REGISTERED_AT_AGENCY);
    }

    @Test
    void aCustomerWhoTurns18TomorrowIsRefused() {
        assertThatThrownBy(() -> register(customer(TODAY.minusYears(18).plusDays(1), "ACTUAL"), Rials.of(1),
                List.of())).isInstanceOf(CustomerUnderageException.class);
    }

    @Test
    void aCompanyIsRefused() {
        assertThatThrownBy(() -> register(customer(LocalDate.of(1990, 5, 17), "LEGAL"), Rials.of(1), List.of()))
                .isInstanceOf(CustomerNotActualException.class);
        assertThatThrownBy(() -> register(customer(LocalDate.of(1990, 5, 17), null), Rials.of(1), List.of()))
                .isInstanceOf(CustomerNotActualException.class);
    }

    @Test
    void anUnknownBirthDateCannotBeCheckedSoItIsTechnical() {
        assertThatThrownBy(() -> register(customer(null, "ACTUAL"), Rials.of(1), List.of()))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void theAmountIsOneToSixteenDigits() {
        var adult = customer(LocalDate.of(1990, 5, 17), "ACTUAL");
        assertThat(register(adult, Rials.of(Request.MAX_AMOUNT), List.of()).amount().value())
                .isEqualTo(9_999_999_999_999_999L);
        assertThatThrownBy(() -> register(adult, Rials.of(Request.MAX_AMOUNT + 1), List.of()))
                .isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> register(adult, Rials.ZERO, List.of()))
                .isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> register(adult, null, List.of())).isInstanceOf(InvalidRequestDataException.class);
    }

    @Test
    void atMostFiveCompanions() {
        var adult = customer(LocalDate.of(1990, 5, 17), "ACTUAL");
        assertThat(register(adult, Rials.of(1), java.util.Collections.nCopies(5, companion())).companions())
                .hasSize(5);
        assertThatThrownBy(() -> register(adult, Rials.of(1), java.util.Collections.nCopies(6, companion())))
                .isInstanceOf(InvalidRequestDataException.class);
    }

    @Test
    void aBranchAndADueDateAreRequired() {
        var adult = customer(LocalDate.of(1990, 5, 17), "ACTUAL");
        assertThatThrownBy(() -> Request.register(UUID.randomUUID(), CODE, AGENCY, " ", Rials.of(1),
                LocalDate.of(2027, 1, 20), adult, List.of(), "x", NOW, TODAY))
                .isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> Request.register(UUID.randomUUID(), CODE, AGENCY, null, Rials.of(1),
                LocalDate.of(2027, 1, 20), adult, List.of(), "x", NOW, TODAY))
                .isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> Request.register(UUID.randomUUID(), CODE, AGENCY, "0123", Rials.of(1), null, adult,
                List.of(), "x", NOW, TODAY)).isInstanceOf(InvalidRequestDataException.class);
    }

    @Test
    void theRefusalsAreBadRequests() {
        assertThat(new CustomerUnderageException().kind()).isEqualTo(DomainException.Kind.BAD_REQUEST);
        assertThat(new CustomerNotActualException().kind()).isEqualTo(DomainException.Kind.BAD_REQUEST);
        assertThat(new InvalidRequestDataException("x").args()).containsExactly("x");
    }

    @Test
    void aCustomerWithoutAMobileNumberIsRefusedBecauseTheSmsCannotBeSent() {
        for (String mobile : new String[] {null, " "}) {
            var noMobile = new CustomerSnapshot("555", "1000000011", "Ali", "Rezaei", "Ali Rezaei", "Reza",
                    LocalDate.of(1990, 5, 17), mobile, null, null, null, "ACTUAL", null);

            assertThatThrownBy(() -> register(noMobile, Rials.of(1), List.of()))
                    .isInstanceOf(CustomerMobileMissingException.class);
        }
        assertThat(new CustomerMobileMissingException().kind()).isEqualTo(DomainException.Kind.BAD_REQUEST);
        assertThat(new NotBankCustomerException().kind()).isEqualTo(DomainException.Kind.BAD_REQUEST);
        assertThat(new BranchNotAvailableException().kind()).isEqualTo(DomainException.Kind.BAD_REQUEST);
        assertThat(new DuplicateRegistrationException().kind()).isEqualTo(DomainException.Kind.CONFLICT);
        assertThat(new AgencyNotActiveException().kind()).isEqualTo(DomainException.Kind.FORBIDDEN);
    }
}
