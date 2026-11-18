package com.sb24.asanzeman.integration.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

class HttpClientsTest {

    @Test
    void buildsAClientWithTheDefaultTrustStore() {
        assertThat(HttpClients.restClient(Duration.ofSeconds(2), Duration.ofSeconds(5), false)).isNotNull();
    }

    @Test
    void buildsAClientThatSkipsCertificateChecks() {
        assertThat(HttpClients.restClient(Duration.ofSeconds(2), Duration.ofSeconds(5), true)).isNotNull();
    }

    @Test
    void trustAllContextIsATlsContext() {
        SSLContext context = HttpClients.trustAllContext();

        assertThat(context.getProtocol()).isEqualTo("TLS");
    }

    @Test
    void unknownProtocolFailsWithAClearMessage() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> HttpClients.trustAllContext("NOT-A-PROTOCOL"))
                .isInstanceOf(IllegalStateException.class).hasMessage("cannot create the TLS context");
    }

    @Test
    void trustAllAcceptsEveryCertificateAndHasNoIssuers() {
        var trust = new HttpClients.TrustAll();

        assertThatCode(() -> {
            trust.checkClientTrusted(null, "RSA");
            trust.checkServerTrusted(null, "RSA");
            trust.checkClientTrusted(null, "RSA", (java.net.Socket) null);
            trust.checkServerTrusted(null, "RSA", (java.net.Socket) null);
            trust.checkClientTrusted(null, "RSA", (javax.net.ssl.SSLEngine) null);
            trust.checkServerTrusted(null, "RSA", (javax.net.ssl.SSLEngine) null);
        }).doesNotThrowAnyException();
        assertThat(trust.getAcceptedIssuers()).isEmpty();
    }
}
