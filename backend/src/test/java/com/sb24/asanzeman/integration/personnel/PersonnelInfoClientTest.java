package com.sb24.asanzeman.integration.personnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sb24.asanzeman.integration.gateway.GatewayBearerInterceptor;
import com.sb24.asanzeman.integration.gateway.GatewayTokenProvider;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

class PersonnelInfoClientTest {

    private static final String URL = "https://apigw.test/GetOverallPersonelInfo";

    private GatewayTokenProvider tokens;
    private MockRestServiceServer server;
    private PersonnelInfoClient client;

    @BeforeEach
    void setUp() {
        tokens = mock(GatewayTokenProvider.class);
        when(tokens.token()).thenReturn("gw-token");
        RestClient.Builder builder = RestClient.builder().requestInterceptor(new GatewayBearerInterceptor(tokens));
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PersonnelInfoClient(builder.build(), URL);
    }

    private static DefaultResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    @Test
    void sendsTheRequestWithTheGatewayTokenAndReadsTheBranch() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer gw-token"))
                .andExpect(content().json("{\"cfcifNO\":0,\"accountName\":\"ali\",\"personelCode\":0}"))
                .andRespond(json("""
                        {"userInfo": {"abrnchcod": 1234, "abrnchcodmain": 999, "prAcvBranch": 888,
                         "prsnCode": "P77", "personelUserName": "ali.u", "aBranchName": "Main branch",
                         "position": "Manager", "job": "Clerk"}}"""));

        PersonnelInfo info = client.find("ali").orElseThrow();

        // only abrnchcod is the branch; abrnchcodmain and prAcvBranch are ignored
        assertThat(info).isEqualTo(new PersonnelInfo("1234", "P77", "ali.u", "Main branch", "Manager", "Clerk"));
        server.verify();
    }

    @Test
    void aBranchCodeThatIsAStringIsKeptAsIs() {
        server.expect(requestTo(URL)).andRespond(json("{\"userInfo\":{\"abrnchcod\":\" 0123 \"}}"));

        PersonnelInfo info = client.find("ali").orElseThrow();

        assertThat(info.branchCode()).isEqualTo("0123");
        assertThat(info.prsnCode()).isNull();
    }

    @Test
    void http404MeansTheUserIsUnknown() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON).body("\"اطلاعات یافت نشد\""));

        assertThat(client.find("ghost")).isEmpty();
    }

    @Test
    void a401RefreshesTheGatewayTokenAndRepeatsTheCall() {
        when(tokens.token()).thenReturn("old", "new");
        server.expect(requestTo(URL)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer old"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(URL)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer new"))
                .andRespond(json("{\"userInfo\":{\"abrnchcod\":7}}"));

        assertThat(client.find("ali").orElseThrow().branchCode()).isEqualTo("7");

        verify(tokens).invalidate();
    }

    @Test
    void aSecond401IsTechnical() {
        when(tokens.token()).thenReturn("old", "new");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.find("ali")).isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void aServerErrorIsTechnical() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.find("ali")).isInstanceOfSatisfying(TemporarilyUnavailableException.class,
                ex -> assertThat(ex.service()).isEqualTo("personnelInfo"));
    }

    @Test
    void aTimeoutIsTechnical() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("read timed out")));

        assertThatThrownBy(() -> client.find("ali")).isInstanceOf(TemporarilyUnavailableException.class)
                .hasRootCauseMessage("read timed out");
    }

    @Test
    void aBrokenBodyIsTechnical() {
        server.expect(requestTo(URL)).andRespond(json("<html>oops</html>"));

        assertThatThrownBy(() -> client.find("ali")).isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void aJsonArrayIsTechnical() {
        server.expect(requestTo(URL)).andRespond(json("[]"));

        assertThatThrownBy(() -> client.find("ali")).isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void noUserInfoMeansNoBranchSoNobodyIsLetIn() {
        server.expect(requestTo(URL)).andRespond(json("{}"));

        assertThatThrownBy(() -> client.find("ali")).isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void aFractionalOrBlankBranchCodeIsUnusable() {
        server.expect(requestTo(URL)).andRespond(json("{\"userInfo\":{\"abrnchcod\":12.5}}"));
        server.expect(requestTo(URL)).andRespond(json("{\"userInfo\":{\"abrnchcod\":\"  \"}}"));
        server.expect(requestTo(URL)).andRespond(json("{\"userInfo\":{\"abrnchcod\":null}}"));

        assertThatThrownBy(() -> client.find("ali")).isInstanceOf(TemporarilyUnavailableException.class);
        assertThatThrownBy(() -> client.find("ali")).isInstanceOf(TemporarilyUnavailableException.class);
        assertThatThrownBy(() -> client.find("ali")).isInstanceOf(TemporarilyUnavailableException.class);
    }
}
