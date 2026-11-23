package com.sb24.asanzeman.integration.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class InquiryClientTest {

    private static final String URL = "https://gw.test/Svc/api/Check";

    private MockRestServiceServer server;

    private InquiryClient client(int retries) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var endpoint = new InquiryEndpoint(null, "/Svc/api/Check", Duration.ofSeconds(5), retries, Map.of());
        return new InquiryClient(builder.build(), "https://gw.test", endpoint, Duration.ZERO);
    }

    @Test
    void postsTheJsonBodyWithTheExtraHeadersAndReturnsStatusAndBody() {
        var client = client(0);
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST)).andExpect(header("ChanelCode", "15"))
                .andExpect(content().json("{\"nationalKey\":\"1000000011\"}"))
                .andRespond(withSuccess("{\"result\":false}", MediaType.APPLICATION_JSON));

        var reply = client.post(Map.of("nationalKey", "1000000011"), Map.of("ChanelCode", "15"));

        assertThat(reply).isEqualTo(new InquiryReply(200, "{\"result\":false}"));
        server.verify();
    }

    @Test
    void getsThePathWithTheSuffix() {
        var client = client(0);
        server.expect(requestTo(URL + "/1000000011")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.get("/1000000011").status()).isEqualTo(200);
    }

    @Test
    void a4xxIsAnAnswerTheInquiryReadsNotAFailure() {
        var client = client(2);
        server.expect(times(1), requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND).body("{\"detail\":\"x\"}"));

        var reply = client.post(Map.of(), Map.of());

        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.body()).contains("detail");
        server.verify(); // not repeated
    }

    @Test
    void a5xxIsRepeatedAndTheLaterSuccessIsTheAnswer() {
        var client = client(2);
        server.expect(times(2), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        assertThat(client.post(Map.of(), Map.of()).body()).isEqualTo("{\"ok\":true}");
        server.verify();
    }

    @Test
    void a5xxThatKeepsComingIsUnavailableAfterTheRetriesAreUsed() {
        var client = client(1);
        server.expect(times(2), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.post(Map.of(), Map.of())).isInstanceOf(InquiryCallException.class)
                .satisfies(e -> assertThat(((InquiryCallException) e).kind())
                        .isEqualTo(InquiryCallException.Kind.UNAVAILABLE));
        server.verify();
    }

    @Test
    void aTimeoutIsReportedAsATimeout() {
        var client = client(0);
        server.expect(requestTo(URL)).andRespond(withException(new HttpTimeoutException("request timed out")));

        assertThatThrownBy(() -> client.post(Map.of(), Map.of())).isInstanceOf(InquiryCallException.class)
                .satisfies(e -> assertThat(((InquiryCallException) e).kind())
                        .isEqualTo(InquiryCallException.Kind.TIMEOUT));
    }

    @Test
    void aDroppedConnectionIsUnavailable() {
        var client = client(0);
        server.expect(requestTo(URL)).andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> client.get("")).isInstanceOf(InquiryCallException.class)
                .satisfies(e -> assertThat(((InquiryCallException) e).kind())
                        .isEqualTo(InquiryCallException.Kind.UNAVAILABLE));
    }

    @Test
    void aCancelledRunStopsTheRetriesAndKeepsTheInterruptFlag() {
        var client = client(5);
        server.expect(times(1), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        Thread.currentThread().interrupt(); // the pause between two tries notices it
        try {
            assertThatThrownBy(() -> client.post(Map.of(), Map.of())).isInstanceOf(InquiryCallException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        server.verify();
    }

    @Test
    void anythingElseThatGoesWrongIsUnavailableToo() {
        var rest = RestClient.builder().requestInterceptor((request, body, execution) -> {
            throw new IllegalStateException("no token");
        }).build();
        var client = new InquiryClient(rest, "https://gw.test",
                new InquiryEndpoint(null, "/x", null, 0, null), Duration.ZERO);

        assertThatThrownBy(() -> client.post(Map.of(), Map.of())).isInstanceOf(InquiryCallException.class)
                .satisfies(e -> assertThat(((InquiryCallException) e).kind())
                        .isEqualTo(InquiryCallException.Kind.UNAVAILABLE));
    }

    @Test
    void theEndpointHasSensibleDefaultsAndANamedMissingFieldStopsTheStartup() {
        var endpoint = new InquiryEndpoint(null, "/x", null, null, null);

        assertThat(endpoint.timeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(endpoint.retries()).isEqualTo(1);
        assertThat(endpoint.fields()).isEmpty();
        assertThatThrownBy(() -> endpoint.field("channel-code")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("channel-code");
        assertThatThrownBy(() -> new InquiryEndpoint(null, "/x", null, null, Map.of("a", " ")).field("a"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(new InquiryEndpoint(null, "/x", null, null, Map.of("a", "1")).field("a")).isEqualTo("1");
    }
}
