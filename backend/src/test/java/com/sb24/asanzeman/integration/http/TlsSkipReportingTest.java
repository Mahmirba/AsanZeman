package com.sb24.asanzeman.integration.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class TlsSkipReportingTest {

    @Test
    void exposesOneWhenCertificateCheckingIsOff() {
        var meters = new SimpleMeterRegistry();

        TlsSkipReporting.report("yaghut", true, meters);

        assertThat(meters.get("azs.tls.skip.verify").tag("service", "yaghut").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void exposesZeroWhenItIsOn() {
        var meters = new SimpleMeterRegistry();

        TlsSkipReporting.report("yaghut", false, meters);

        assertThat(meters.get("azs.tls.skip.verify").tag("service", "yaghut").gauge().value()).isEqualTo(0.0);
    }
}
