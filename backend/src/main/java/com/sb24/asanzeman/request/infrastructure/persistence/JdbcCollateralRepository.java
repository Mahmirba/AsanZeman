package com.sb24.asanzeman.request.infrastructure.persistence;

import com.sb24.asanzeman.request.domain.model.CollateralAllocation;
import com.sb24.asanzeman.request.domain.model.CollateralPlan;
import com.sb24.asanzeman.request.domain.port.out.CollateralRepositoryPort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code AZS_BUSINESS.collateral} through plain JDBC (4.3). A new choice replaces the old one in one transaction, so a
 * request never holds half of two choices.
 */
@Component
public class JdbcCollateralRepository implements CollateralRepositoryPort {

    private final JdbcClient jdbc;

    public JdbcCollateralRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void replace(UUID requestId, CollateralPlan plan, Instant at) {
        byte[] request = RawUuid.toBytes(requestId);
        jdbc.sql("DELETE FROM azs_business.collateral WHERE request_id = :request")
                .param("request", request, Types.VARBINARY).update();
        for (CollateralAllocation a : plan.allocations()) {
            jdbc.sql("""
                    INSERT INTO azs_business.collateral (request_id, deposit_number, kind, price_class, share_percent,
                                                         factor, allocated, collateral, created_at)
                    VALUES (:request, :deposit, :kind, :priceClass, :share, :factor, :allocated, :collateral, :at)
                    """)
                    .param("request", request, Types.VARBINARY)
                    .param("deposit", a.depositNumber(), Types.VARCHAR)
                    .param("kind", a.kind().name(), Types.VARCHAR)
                    .param("priceClass", a.priceClass().name(), Types.VARCHAR)
                    .param("share", a.sharePercent(), Types.INTEGER)
                    .param("factor", a.factor(), Types.NUMERIC)
                    .param("allocated", a.allocated().value(), Types.BIGINT)
                    .param("collateral", a.collateral().value(), Types.BIGINT)
                    .param("at", DbTime.toDb(at), Types.TIMESTAMP).update();
        }
    }

    @Override
    public boolean exists(UUID requestId) {
        return jdbc.sql("SELECT COUNT(*) FROM azs_business.collateral WHERE request_id = :request")
                .param("request", RawUuid.toBytes(requestId), Types.VARBINARY).query(Long.class).single() > 0;
    }
}
