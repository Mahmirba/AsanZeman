package com.sb24.asanzeman.integration.http;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** D38: when a service has {@code tls.skip-verify=true}, say so in the log at startup and expose it as a metric. */
public final class TlsSkipReporting {

    static final String METRIC = "azs.tls.skip.verify";
    private static final Logger log = LoggerFactory.getLogger(TlsSkipReporting.class);

    private TlsSkipReporting() {
    }

    public static void report(String service, boolean skipVerify, MeterRegistry meters) {
        if (skipVerify) {
            log.warn("TLS certificate checking is OFF for {} (azs.integration.{}.tls.skip-verify=true)", service,
                    service);
        }
        Gauge.builder(METRIC, () -> skipVerify ? 1 : 0).tag("service", service).register(meters);
    }
}
