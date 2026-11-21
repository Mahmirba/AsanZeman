package com.sb24.asanzeman.request.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.TrackingCode;
import com.sb24.asanzeman.shared.model.JalaliDate;
import com.sb24.asanzeman.shared.model.Rials;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import com.sb24.asanzeman.support.H2Auth;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real SQL on in-memory H2. */
class JdbcRequestRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final String NATIONAL_CODE = "0012345679";

    private final JdbcClient jdbc = H2Auth.newDatabase();
    private final JdbcRequestRepository repository = new JdbcRequestRepository(jdbc);
    private final UUID agency = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc.sql("INSERT INTO azs_business.agency (id, cif, code, name) VALUES (:id, '9001', '0042', 'Sky')")
                .param("id", RawUuid.toBytes(agency), Types.VARBINARY).update();
        jdbc.sql("""
                INSERT INTO azs_business.branch (branch_code, name, parent_branch, last_synced_at)
                VALUES ('0123', 'Main', '0123', CURRENT_TIMESTAMP)""").update();
    }

    private Request request(int counter, List<Companion> companions) {
        return RequestRepositoryFixtures.request(agency, TrackingCode.of("0123", "0042", JalaliDate.of(1405, 8, 30),
                counter), companions, NOW);
    }

    @Test
    void aStoredRequestIsReadBackWithEveryField() {
        Request saved = request(1, List.of(
                new Companion("Sara Rezaei", NATIONAL_CODE, LocalDate.of(2015, 1, 1), "P1"),
                new Companion("Omid Rezaei", NATIONAL_CODE, LocalDate.of(2017, 2, 3), "P2")));

        repository.insert(saved);

        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void companionsKeepTheirOrder() {
        var a = new Companion("A", NATIONAL_CODE, LocalDate.of(2015, 1, 1), "P1");
        var b = new Companion("B", NATIONAL_CODE, LocalDate.of(2016, 1, 1), "P2");
        var c = new Companion("C", NATIONAL_CODE, LocalDate.of(2017, 1, 1), "P3");
        Request saved = request(1, List.of(c, a, b));

        repository.insert(saved);

        assertThat(repository.findById(saved.id()).orElseThrow().companions()).containsExactly(c, a, b);
    }

    @Test
    void aRequestWithoutCompanionsAndOptionalDetailsRoundTrips() {
        Request saved = RequestRepositoryFixtures.bare(agency, request(2, List.of()).trackingCode(), NOW);

        repository.insert(saved);

        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void anUnknownIdIsEmpty() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void aTrackingCodeIsUnique() {
        repository.insert(request(1, List.of()));

        assertThatThrownBy(() -> repository.insert(request(1, List.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theAmountKeepsAllSixteenDigits() {
        Request big = RequestRepositoryFixtures.withAmount(request(3, List.of()), Rials.of(Request.MAX_AMOUNT));

        repository.insert(big);

        assertThat(repository.findById(big.id()).orElseThrow().amount()).isEqualTo(Rials.of(Request.MAX_AMOUNT));
    }

    @Test
    void aRequestInsideTheWindowIsFoundForTheSameAgencyAndCustomer() {
        repository.insert(request(4, List.of()));
        Instant since = NOW.minusSeconds(10);

        assertThat(repository.existsSince(agency, "1000000011", since)).isTrue();
        assertThat(repository.existsSince(agency, "1000000011", NOW)).isTrue();
    }

    @Test
    void aRequestBeforeTheWindowIsNotFound() {
        repository.insert(request(5, List.of()));

        assertThat(repository.existsSince(agency, "1000000011", NOW.plusSeconds(1))).isFalse();
    }

    @Test
    void anotherCustomerOrAnotherAgencyDoesNotCount() {
        repository.insert(request(6, List.of()));
        Instant since = NOW.minusSeconds(10);

        assertThat(repository.existsSince(agency, "1000000028", since)).isFalse();
        assertThat(repository.existsSince(UUID.randomUUID(), "1000000011", since)).isFalse();
    }
}
