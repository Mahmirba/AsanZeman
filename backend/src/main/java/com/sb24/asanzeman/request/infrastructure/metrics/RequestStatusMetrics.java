package com.sb24.asanzeman.request.infrastructure.metrics;

import com.sb24.asanzeman.request.domain.model.RequestStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The number of requests in each status, for the dashboard (Phase 9): {@code azs.requests{status=..., type=...}}. One
 * grouped query answers every gauge, and its result is kept for {@link #CACHE} so a scrape never costs more than one
 * cheap query every few seconds, however many gauges Prometheus reads.
 */
@Component
public class RequestStatusMetrics implements MeterBinder {

    static final Duration CACHE = Duration.ofSeconds(15);

    private final JdbcClient jdbc;
    private Map<String, Long> counts = Map.of();
    private long readAt = Long.MIN_VALUE;

    public RequestStatusMetrics(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (String type : new String[] {"ISSUANCE", "REVOCATION"}) {
            for (RequestStatus status : RequestStatus.values()) {
                Gauge.builder("azs.requests", () -> count(type, status)).tag("type", type)
                        .tag("status", status.name()).register(registry);
            }
        }
    }

    private double count(String type, RequestStatus status) {
        return counts().getOrDefault(type + "/" + status.name(), 0L);
    }

    private synchronized Map<String, Long> counts() {
        long now = System.nanoTime();
        if (readAt == Long.MIN_VALUE || now - readAt >= CACHE.toNanos()) {
            Map<String, Long> read = new HashMap<>();
            jdbc.sql("SELECT request_type, status, COUNT(*) AS n FROM azs_business.request GROUP BY request_type, status")
                    .query((rs, row) -> read.put(rs.getString("request_type") + "/" + rs.getString("status"),
                            rs.getLong("n")))
                    .list();
            counts = read;
            readAt = now;
        }
        return counts;
    }
}
