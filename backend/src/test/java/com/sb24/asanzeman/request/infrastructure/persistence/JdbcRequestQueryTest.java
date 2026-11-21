package com.sb24.asanzeman.request.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestSearchCriteria;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.model.TrackingCode;
import com.sb24.asanzeman.shared.model.JalaliDate;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import com.sb24.asanzeman.support.H2Auth;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real SQL on in-memory H2. */
class JdbcRequestQueryTest {

    private static final Instant T0 = Instant.parse("2026-11-21T10:00:00Z");

    private final JdbcClient jdbc = H2Auth.newDatabase();
    private final JdbcRequestRepository repository = new JdbcRequestRepository(jdbc);
    private final JdbcRequestQuery query = new JdbcRequestQuery(jdbc);
    private final UUID agency = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        agency(agency, "9001", "0042", "Sky Travel");
        agency(other, "9002", "0043", "Other Air");
        jdbc.sql("""
                INSERT INTO azs_business.branch (branch_code, name, parent_branch, last_synced_at)
                VALUES ('0123', 'Valiasr', '0123', CURRENT_TIMESTAMP)""").update();
    }

    private void agency(UUID id, String cif, String code, String name) {
        jdbc.sql("INSERT INTO azs_business.agency (id, cif, code, name) VALUES (:id, :cif, :code, :name)")
                .param("id", RawUuid.toBytes(id), Types.VARBINARY).param("cif", cif).param("code", code)
                .param("name", name).update();
    }

    private Request save(UUID agencyId, int counter, String customerCode, String cif, String name, Instant at,
            List<Companion> companions) {
        Request base = RequestRepositoryFixtures.request(agencyId, TrackingCode.of("0123", "0042",
                JalaliDate.of(1405, 8, 30), counter), companions, at);
        var c = base.customer();
        Request r = new Request(base.id(), base.trackingCode(), base.type(), base.status(), agencyId,
                base.branchCode(), base.amount(), base.dueDate(),
                new com.sb24.asanzeman.request.domain.model.CustomerSnapshot(cif, customerCode, null, null, name,
                        c.fatherName(), c.birthDate(), c.mobile(), null, null, null, "ACTUAL", null),
                companions, "Sky Travel", at);
        repository.insert(r);
        return r;
    }

    private RequestSearchCriteria criteria() {
        return new RequestSearchCriteria(agency, null, null, null, null, null, null, null);
    }

    private static RequestSearchCriteria with(RequestSearchCriteria c, String code, String cif, Set<RequestType> types,
            String tracking, String name, Instant from, Instant to) {
        return new RequestSearchCriteria(c.agencyId(), code, cif, types, tracking, name, from, to);
    }

    @Test
    void listsOnlyTheAgenciesOwnRequestsNewestFirst() {
        Request older = save(agency, 1, "1000000011", "555", "Ali Rezaei", T0, List.of());
        Request newer = save(agency, 2, "1000000028", "556", "Sara Karimi", T0.plusSeconds(60), List.of());
        save(other, 3, "1000000036", "557", "Not Mine", T0.plusSeconds(120), List.of());

        PageResult<RequestSummary> page = query.search(criteria(), 0, 20);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(RequestSummary::id).containsExactly(newer.id(), older.id());
    }

    @Test
    void aRowCarriesTheBranchNameTheAgencyNameAndTheCustomerDetails() {
        Request saved = save(agency, 1, "1000000011", "555", "Ali Rezaei", T0, List.of());

        RequestSummary row = query.search(criteria(), 0, 20).items().get(0);

        assertThat(row.id()).isEqualTo(saved.id());
        assertThat(row.trackingCode()).isEqualTo(saved.trackingCode().value());
        assertThat(row.type()).isEqualTo(RequestType.ISSUANCE);
        assertThat(row.status()).isEqualTo(saved.status());
        assertThat(row.customerCode()).isEqualTo("1000000011");
        assertThat(row.customerCif()).isEqualTo("555");
        assertThat(row.customerName()).isEqualTo("Ali Rezaei");
        assertThat(row.amount()).isEqualTo(250_000_000L);
        assertThat(row.branchCode()).isEqualTo("0123");
        assertThat(row.branchName()).isEqualTo("Valiasr");
        assertThat(row.dueDate()).isEqualTo(LocalDate.of(2027, 1, 20));
        assertThat(row.registeredAt()).isEqualTo(T0);
        assertThat(row.agencyName()).isEqualTo("Sky Travel");
        assertThat(row.customerMobile()).isEqualTo("09121234567");
    }

    @Test
    void pagesAreCutByOffsetAndTheTotalCountsEverything() {
        for (int i = 1; i <= 5; i++) {
            save(agency, i, "1000000011", "555", "Ali", T0.plusSeconds(i), List.of());
        }

        PageResult<RequestSummary> second = query.search(criteria(), 1, 2);

        assertThat(second.total()).isEqualTo(5);
        assertThat(second.items()).hasSize(2);
        assertThat(second.page()).isEqualTo(1);
        assertThat(second.items().get(0).registeredAt()).isEqualTo(T0.plusSeconds(3));
        assertThat(query.search(criteria(), 2, 2).items()).hasSize(1);
        assertThat(query.search(criteria(), 3, 2).items()).isEmpty();
    }

    @Test
    void anEmptyResultIsAnEmptyPage() {
        PageResult<RequestSummary> page = query.search(criteria(), 0, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    @Test
    void filtersByNationalCodeAndCustomerNumber() {
        save(agency, 1, "1000000011", "555", "Ali", T0, List.of());
        save(agency, 2, "1000000028", "556", "Sara", T0.plusSeconds(1), List.of());

        assertThat(query.search(with(criteria(), "1000000028", null, null, null, null, null, null), 0, 20).items())
                .extracting(RequestSummary::customerCode).containsExactly("1000000028");
        assertThat(query.search(with(criteria(), null, "555", null, null, null, null, null), 0, 20).items())
                .extracting(RequestSummary::customerCif).containsExactly("555");
    }

    @Test
    void filtersByTrackingCode() {
        Request one = save(agency, 1, "1000000011", "555", "Ali", T0, List.of());
        save(agency, 2, "1000000011", "555", "Ali", T0.plusSeconds(1), List.of());

        var found = query.search(with(criteria(), null, null, null, one.trackingCode().value(), null, null, null), 0,
                20);

        assertThat(found.items()).extracting(RequestSummary::id).containsExactly(one.id());
    }

    @Test
    void filtersByPartOfTheNameAndTreatsWildcardsAsPlainText() {
        save(agency, 1, "1000000011", "555", "Ali Rezaei", T0, List.of());
        save(agency, 2, "1000000028", "556", "Sara 50% Karimi", T0.plusSeconds(1), List.of());

        assertThat(query.search(with(criteria(), null, null, null, null, "Rez", null, null), 0, 20).items())
                .extracting(RequestSummary::customerName).containsExactly("Ali Rezaei");
        assertThat(query.search(with(criteria(), null, null, null, null, "50%", null, null), 0, 20).items())
                .extracting(RequestSummary::customerName).containsExactly("Sara 50% Karimi");
        assertThat(query.search(with(criteria(), null, null, null, null, "%", null, null), 0, 20).total()).isEqualTo(1);
        assertThat(query.search(with(criteria(), null, null, null, null, "_", null, null), 0, 20).total()).isZero();
    }

    @Test
    void filtersByTypeAndAnEmptySetMeansBoth() {
        save(agency, 1, "1000000011", "555", "Ali", T0, List.of());

        assertThat(query.search(with(criteria(), null, null, Set.of(RequestType.ISSUANCE), null, null, null, null), 0,
                20).total()).isEqualTo(1);
        assertThat(query.search(with(criteria(), null, null, Set.of(RequestType.REVOCATION), null, null, null, null),
                0, 20).total()).isZero();
        assertThat(query.search(with(criteria(), null, null, Set.of(RequestType.ISSUANCE, RequestType.REVOCATION),
                null, null, null, null), 0, 20).total()).isEqualTo(1);
        assertThat(query.search(with(criteria(), null, null, Set.of(), null, null, null, null), 0, 20).total())
                .isEqualTo(1);
    }

    @Test
    void theRegistrationWindowIncludesItsStartAndExcludesItsEnd() {
        save(agency, 1, "1000000011", "555", "Ali", T0, List.of());
        save(agency, 2, "1000000011", "555", "Ali", T0.plusSeconds(100), List.of());

        assertThat(query.search(with(criteria(), null, null, null, null, null, T0, T0.plusSeconds(100)), 0, 20)
                .items()).extracting(RequestSummary::registeredAt).containsExactly(T0);
        assertThat(query.search(with(criteria(), null, null, null, null, null, T0.plusSeconds(1), null), 0, 20)
                .total()).isEqualTo(1);
        assertThat(query.search(with(criteria(), null, null, null, null, null, null, T0), 0, 20).total()).isZero();
    }

    @Test
    void blankFiltersAreIgnored() {
        save(agency, 1, "1000000011", "555", "Ali", T0, List.of());

        assertThat(query.search(with(criteria(), " ", " ", null, " ", " ", null, null), 0, 20).total()).isEqualTo(1);
    }

    @Test
    void detailsCarryTheCompanionsInOrderForTheAgenciesOwnRequestOnly() {
        var a = new Companion("A", "0012345679", LocalDate.of(2015, 1, 1), "P1");
        var b = new Companion("B", "0012345679", LocalDate.of(2016, 1, 1), "P2");
        Request mine = save(agency, 1, "1000000011", "555", "Ali", T0, List.of(b, a));
        Request theirs = save(other, 2, "1000000011", "555", "Ali", T0, List.of());

        var details = query.details(agency, mine.id()).orElseThrow();

        assertThat(details.summary().id()).isEqualTo(mine.id());
        assertThat(details.companions()).containsExactly(b, a);
        assertThat(query.details(agency, theirs.id())).isEmpty();
        assertThat(query.details(agency, UUID.randomUUID())).isEmpty();
    }

    @Test
    void theLikeEscapingMakesPercentUnderscoreAndBackslashPlainText() {
        assertThat(JdbcRequestQuery.escapeLike("50%_a\\b")).isEqualTo("50\\%\\_a\\\\b");
    }
}
