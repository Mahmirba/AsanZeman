package com.sb24.asanzeman.request.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import com.sb24.asanzeman.support.H2Auth;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Types;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class RequestStatusMetricsTest {

    private final JdbcClient jdbc = H2Auth.newDatabase();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final UUID agency = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc.sql("INSERT INTO azs_business.agency (id, cif, code, name) VALUES (:id, '9001', '0042', 'Sky')")
                .param("id", RawUuid.toBytes(agency), Types.VARBINARY).update();
        jdbc.sql("INSERT INTO azs_business.branch (branch_code, name, parent_branch, last_synced_at) "
                + "VALUES ('0123', 'Valiasr', '0123', CURRENT_TIMESTAMP)").update();
    }

    private void insert(String type, String status, String code) {
        jdbc.sql("INSERT INTO azs_business.request (id, tracking_code, request_type, status, agency_id, branch_code, "
                + "amount, due_date, customer_code, registered_by, registered_at) VALUES (:id, :code, :type, :status, "
                + ":agency, '0123', 1000, CURRENT_DATE, '1000000011', 'x', CURRENT_TIMESTAMP)")
                .param("id", RawUuid.toBytes(UUID.randomUUID()), Types.VARBINARY).param("code", code)
                .param("type", type).param("status", status)
                .param("agency", RawUuid.toBytes(agency), Types.VARBINARY).update();
    }

    private double gauge(String type, RequestStatus status) {
        return registry.get("azs.requests").tag("type", type).tag("status", status.name()).gauge().value();
    }

    @Test
    void everyTypeAndStatusHasAGaugeWithItsCount() {
        insert("ISSUANCE", "ISSUED", "c1");
        insert("ISSUANCE", "ISSUED", "c2");
        insert("ISSUANCE", "REJECTED", "c3");
        insert("REVOCATION", "UNDER_ACTION", "c4");

        new RequestStatusMetrics(jdbc).bindTo(registry);

        assertThat(gauge("ISSUANCE", RequestStatus.ISSUED)).isEqualTo(2.0);
        assertThat(gauge("ISSUANCE", RequestStatus.REJECTED)).isEqualTo(1.0);
        assertThat(gauge("REVOCATION", RequestStatus.UNDER_ACTION)).isEqualTo(1.0);
        assertThat(gauge("ISSUANCE", RequestStatus.CANCELLED)).isZero();
        assertThat(registry.find("azs.requests").gauges()).hasSize(RequestStatus.values().length * 2);
    }

    @Test
    void theCountsAreKeptForAWhileSoOneScrapeIsOneQuery() {
        insert("ISSUANCE", "ISSUED", "c1");
        new RequestStatusMetrics(jdbc).bindTo(registry);
        assertThat(gauge("ISSUANCE", RequestStatus.ISSUED)).isEqualTo(1.0);

        insert("ISSUANCE", "ISSUED", "c2");

        assertThat(gauge("ISSUANCE", RequestStatus.ISSUED)).isEqualTo(1.0); // still the cached answer
    }
}
