package com.sb24.asanzeman.request.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.request.domain.model.CollateralAllocation;
import com.sb24.asanzeman.request.domain.model.CollateralKind;
import com.sb24.asanzeman.request.domain.model.CollateralPlan;
import com.sb24.asanzeman.request.domain.model.PriceClass;
import com.sb24.asanzeman.shared.model.Rials;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import com.sb24.asanzeman.support.H2Auth;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real SQL on in-memory H2. */
class JdbcCollateralRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    private final JdbcClient jdbc = H2Auth.newDatabase();
    private final JdbcCollateralRepository repository = new JdbcCollateralRepository(jdbc);

    private static CollateralAllocation cash(String deposit, long amount) {
        return new CollateralAllocation(deposit, CollateralKind.CASH, PriceClass.CHEAP, 20, BigDecimal.ONE,
                Rials.of(amount), Rials.of(amount));
    }

    private static CollateralAllocation pledge(String deposit, long base, long collateral) {
        return new CollateralAllocation(deposit, CollateralKind.PLEDGE, PriceClass.EXPENSIVE, 80,
                new BigDecimal("1.05"), Rials.of(base), Rials.of(collateral));
    }

    private static CollateralPlan plan(CollateralAllocation... allocations) {
        return new CollateralPlan(List.of(allocations), Rials.of(200_000_000L), Rials.of(800_000_000L),
                Rials.of(1_040_000_000L));
    }

    private long rows(UUID request) {
        return jdbc.sql("SELECT COUNT(*) FROM azs_business.collateral WHERE request_id = :r")
                .param("r", RawUuid.toBytes(request), Types.VARBINARY).query(Long.class).single();
    }

    @Test
    void aRequestWithoutACollateralChoiceHasNone() {
        assertThat(repository.exists(UUID.randomUUID())).isFalse();
    }

    @Test
    void theChosenCollateralIsSavedOneRowPerAllocation() {
        UUID request = UUID.randomUUID();

        repository.replace(request, plan(cash("D-100", 200_000_000L), pledge("D-300", 800_000_000L, 840_000_000L)),
                NOW);

        assertThat(repository.exists(request)).isTrue();
        assertThat(rows(request)).isEqualTo(2);
        var pledge = jdbc.sql("""
                SELECT deposit_number, kind, price_class, share_percent, factor, allocated, collateral
                  FROM azs_business.collateral WHERE request_id = :r AND kind = 'PLEDGE'
                """).param("r", RawUuid.toBytes(request), Types.VARBINARY)
                .query((rs, row) -> List.of(rs.getString("deposit_number"), rs.getString("price_class"),
                        rs.getInt("share_percent"), rs.getBigDecimal("factor"), rs.getLong("allocated"),
                        rs.getLong("collateral"))).single();
        assertThat(pledge).containsExactly("D-300", "EXPENSIVE", 80, new BigDecimal("1.05"), 800_000_000L,
                840_000_000L);
    }

    @Test
    void theSavedAllocationsAreReadBackCashFirstWithEveryDetail() {
        UUID request = UUID.randomUUID();
        repository.replace(request, plan(pledge("D-300", 800_000_000L, 840_000_000L), cash("D-100", 200_000_000L)),
                NOW);

        var found = repository.find(request);

        assertThat(found).extracting(CollateralAllocation::depositNumber, CollateralAllocation::kind)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("D-100", CollateralKind.CASH),
                        org.assertj.core.groups.Tuple.tuple("D-300", CollateralKind.PLEDGE));
        var pledge = found.get(1);
        assertThat(pledge.priceClass()).isEqualTo(PriceClass.EXPENSIVE);
        assertThat(pledge.sharePercent()).isEqualTo(80);
        assertThat(pledge.factor()).isEqualByComparingTo("1.05");
        assertThat(pledge.allocated()).isEqualTo(Rials.of(800_000_000L));
        assertThat(pledge.collateral()).isEqualTo(Rials.of(840_000_000L));
        assertThat(repository.find(UUID.randomUUID())).isEmpty();
    }

    @Test
    void aNewChoiceReplacesTheOldOneAndNeverTouchesAnotherRequest() {
        UUID request = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        repository.replace(request, plan(cash("OLD", 1), pledge("OLD2", 10, 11)), NOW);
        repository.replace(other, plan(cash("KEEP", 5)), NOW);

        repository.replace(request, plan(cash("NEW", 1_000_000_000L)), NOW.plusSeconds(60));

        assertThat(rows(request)).isEqualTo(1);
        assertThat(jdbc.sql("SELECT deposit_number FROM azs_business.collateral WHERE request_id = :r")
                .param("r", RawUuid.toBytes(request), Types.VARBINARY).query(String.class).single()).isEqualTo("NEW");
        assertThat(rows(other)).isEqualTo(1);
    }
}
