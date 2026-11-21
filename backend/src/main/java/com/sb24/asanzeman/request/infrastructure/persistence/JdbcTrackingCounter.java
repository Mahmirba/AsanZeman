package com.sb24.asanzeman.request.infrastructure.persistence;

import com.sb24.asanzeman.request.domain.model.TrackingCodesExhaustedException;
import com.sb24.asanzeman.request.domain.port.out.TrackingCounterPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code AZS_BUSINESS.tracking_counter} (D12). The increment is one conditional {@code UPDATE}, which locks the row
 * until the registration commits, so two registrations can never get the same number. The first request of a day
 * inserts the row; if two do that at once, the loser retries and increments instead.
 */
@Component
public class JdbcTrackingCounter implements TrackingCounterPort {

    static final int MAX = 9999;
    static final String INCREMENT = """
            UPDATE azs_business.tracking_counter SET last_value = last_value + 1
             WHERE branch_code = :branch AND agency_code = :agency AND day = :day AND last_value < 9999
            """;
    static final String SELECT = """
            SELECT last_value FROM azs_business.tracking_counter
             WHERE branch_code = :branch AND agency_code = :agency AND day = :day
            """;
    static final String INSERT = """
            INSERT INTO azs_business.tracking_counter (branch_code, agency_code, day, last_value)
            VALUES (:branch, :agency, :day, 1)
            """;

    private final JdbcClient jdbc;

    public JdbcTrackingCounter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int next(String branchCode, String agencyCode, String day) {
        while (increment(branchCode, agencyCode, day) == 0) {
            Integer current = current(branchCode, agencyCode, day);
            if (current != null && current >= MAX) {
                throw new TrackingCodesExhaustedException();
            }
            if (current == null) {
                try {
                    jdbc.sql(INSERT).param("branch", branchCode).param("agency", agencyCode).param("day", day)
                            .update();
                    return 1;
                } catch (DuplicateKeyException e) {
                    // another registration created the row first: increment its row on the next round
                }
            }
        }
        return current(branchCode, agencyCode, day);
    }

    private int increment(String branchCode, String agencyCode, String day) {
        return jdbc.sql(INCREMENT).param("branch", branchCode).param("agency", agencyCode).param("day", day).update();
    }

    private Integer current(String branchCode, String agencyCode, String day) {
        return jdbc.sql(SELECT).param("branch", branchCode).param("agency", agencyCode).param("day", day)
                .query(Integer.class).optional().orElse(null);
    }
}
