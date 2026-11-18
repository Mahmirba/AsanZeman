package com.sb24.asanzeman.integration.arcaptcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ArcaptchaClientTest {

    private static final String URL = "https://captcha.test/api/arcaptcha/api/verify";

    private MockRestServiceServer server;
    private ArcaptchaClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ArcaptchaClient(builder.build(), URL);
    }

    @Test
    void sendsTheTokenAndKeysAndReadsSuccess() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"challenge_id\":\"tok\",\"site_key\":\"sk\",\"secret_key\":\"sec\"}"))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        ArcaptchaVerifyResponse response = client.verify("sk", "sec", "tok");

        assertThat(response.success()).isTrue();
        assertThat(response.errorCodes()).isEmpty();
        server.verify();
    }

    @Test
    void readsErrorCodesAndIgnoresUnknownFields() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"],\"hostname\":\"x\"}",
                MediaType.APPLICATION_JSON));

        ArcaptchaVerifyResponse response = client.verify("sk", "sec", "tok");

        assertThat(response.success()).isFalse();
        assertThat(response.errorCodes()).containsExactly("timeout-or-duplicate");
    }

    @Test
    void clientErrorWithCodesIsReportedAsAFailureWithThoseCodes() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false,\"error-codes\":[\"invalid-input-sitekey\"]}"));

        ArcaptchaVerifyResponse response = client.verify("sk", "sec", "tok");

        assertThat(response.success()).isFalse();
        assertThat(response.errorCodes()).containsExactly("invalid-input-sitekey");
    }

    @Test
    void clientErrorWithoutCodesIsABadRequest() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"success\":false}"));

        assertThat(client.verify("sk", "sec", "tok").errorCodes()).containsExactly("bad-request");
    }

    @Test
    void clientErrorWithAnUnreadableBodyIsABadRequest() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("not json at all"));

        ArcaptchaVerifyResponse response = client.verify("sk", "sec", "tok");

        assertThat(response.success()).isFalse();
        assertThat(response.errorCodes()).containsExactly("bad-request");
    }

    @Test
    void serverErrorIsTemporarilyUnavailable() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.verify("sk", "sec", "tok"))
                .isInstanceOfSatisfying(TemporarilyUnavailableException.class,
                        ex -> assertThat(ex.service()).isEqualTo("ARcaptcha"));
    }

    @Test
    void timeoutOrNetworkErrorIsTemporarilyUnavailable() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("read timed out")));

        assertThatThrownBy(() -> client.verify("sk", "sec", "tok"))
                .isInstanceOf(TemporarilyUnavailableException.class).hasRootCauseMessage("read timed out");
    }

    @Test
    void emptyAnswerIsTemporarilyUnavailable() {
        server.expect(requestTo(URL)).andRespond(withSuccess());

        assertThatThrownBy(() -> client.verify("sk", "sec", "tok"))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }
}
