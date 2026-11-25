package com.sb24.asanzeman.integration.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class GatewayBearerInterceptorTest {

    private static final String URL = "https://gw.test/inquiry";

    private GatewayTokenProvider tokens;
    private MockRestServiceServer server;
    private RestClient client;

    @BeforeEach
    void setUp() {
        tokens = mock(GatewayTokenProvider.class);
        RestClient.Builder builder = RestClient.builder().requestInterceptor(new GatewayBearerInterceptor(tokens));
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
    }

    @Test
    void addsTheBearerToken() {
        when(tokens.token()).thenReturn("t1");
        server.expect(requestTo(URL)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer t1"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        assertThat(client.get().uri(URL).retrieve().body(String.class)).isEqualTo("ok");

        server.verify();
        verify(tokens, never()).invalidate();
    }

    @Test
    void a401RefreshesTheTokenAndRepeatsTheCallOnce() {
        when(tokens.token()).thenReturn("old", "new");
        server.expect(requestTo(URL)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer old"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(URL)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer new"))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        assertThat(client.get().uri(URL).retrieve().body(String.class)).isEqualTo("ok");

        server.verify();
        verify(tokens, times(1)).invalidate();
    }

    @Test
    void aSecond401IsHandedBackToTheCaller() {
        when(tokens.token()).thenReturn("old", "new");
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.get().uri(URL).retrieve().body(String.class))
                .isInstanceOf(HttpClientErrorException.Unauthorized.class);

        server.verify();
        verify(tokens, times(1)).invalidate();
    }
}
