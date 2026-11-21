package com.sb24.asanzeman.integration.notifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NotifierClientTest {

    private static final String URL = "https://notifier.test/api/v1/sms/SendSimpleSms";

    private MockRestServiceServer server;
    private NotifierClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NotifierClient(builder.build(), "https://notifier.test", "svc", "secret", "AZS");
    }

    @Test
    void sendsOneRecipientWithTheCredentialsAndReturnsTheSmsId() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"user":"svc","pass":"secret","ClientName":"AZS","numbers":["0989121234567"],
                         "message":"hello"}""", true))
                .andRespond(withSuccess("""
                        {"retval":[{"number":"0989121234567","smsId":"SMS-77","sId":null}]}""",
                        MediaType.APPLICATION_JSON));

        assertThat(client.sendSimpleSms("0989121234567", "hello")).isEqualTo("SMS-77");
        server.verify();
    }

    @Test
    void aNumericSmsIdIsReadAsText() {
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"retval":[{"number":"0989121234567","smsId":4711,"sId":null}]}""", MediaType.APPLICATION_JSON));

        assertThat(client.sendSimpleSms("0989121234567", "hello")).isEqualTo("4711");
    }

    @Test
    void a400IsRejectedAndNeverRetried() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"Request.numbers is not in the correct format.\"}"));

        assertThatThrownBy(() -> client.sendSimpleSms("12", "hello")).isInstanceOf(NotifierRejectedException.class)
                .hasMessageContaining("not in the correct format");
    }

    @Test
    void a403IsACredentialsProblem() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.FORBIDDEN)
                .body("please contact the SamanBank Sms Center support"));

        assertThatThrownBy(() -> client.sendSimpleSms("0989121234567", "hello"))
                .isInstanceOf(NotifierCredentialsException.class).hasMessageContaining("Sms Center support");
    }

    @Test
    void a500IsTechnical() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));

        assertThatThrownBy(() -> client.sendSimpleSms("0989121234567", "hello"))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void aNetworkErrorIsTechnical() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("timeout")));

        assertThatThrownBy(() -> client.sendSimpleSms("0989121234567", "hello"))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"retval\":[]}", "{\"retval\":[{\"smsId\":null}]}", "{\"retval\":[{\"smsId\":\" \"}]}",
            "{}", "<html>proxy</html>"})
    void aSuccessWithoutASmsIdIsTechnicalBecauseWeCannotTellWhatHappened(String body) {
        server.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.sendSimpleSms("0989121234567", "hello"))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }
}
