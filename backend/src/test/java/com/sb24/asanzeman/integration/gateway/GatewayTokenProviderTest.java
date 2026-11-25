package com.sb24.asanzeman.integration.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.MockRestResponseCreators;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** Runs the real Spring Security OAuth2 client against a stubbed token endpoint. */
class GatewayTokenProviderTest {

    private static final String URL = "https://gw.test/connect/token";
    private static final Duration REFRESH_BEFORE = Duration.ofMinutes(5);
    private static final Duration BACKOFF = Duration.ofMillis(500);

    /**
     * A clock the test moves by hand. It starts at the real "now" because the OAuth2 client stamps a token's expiry
     * with the system clock; only the "is it still fresh?" comparison uses this clock.
     */
    static final class MutableClock extends Clock {
        private volatile Instant now = Instant.now();

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private MockRestServiceServer server;
    private MutableClock clock;
    private List<Duration> pauses;
    private GatewayTokenProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = GatewayTokenRestClients.configure(RestClient.builder());
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock();
        pauses = new ArrayList<>();
        provider = GatewayTokenProvider.create(URL, "my-id", "my-secret", builder.build(), REFRESH_BEFORE, clock, 2,
                BACKOFF, pauses::add);
    }

    private static String tokenJson(String token, long expiresIn) {
        return "{\"access_token\":\"" + token + "\",\"token_type\":\"Bearer\",\"expires_in\":" + expiresIn + "}";
    }

    private void expectToken(String token, long expiresIn) {
        server.expect(requestTo(URL)).andRespond(withSuccess(tokenJson(token, expiresIn), MediaType.APPLICATION_JSON));
    }

    @Test
    void sendsTheCredentialsInTheFormBodyNotInABasicHeader() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", "my-id");
        form.add("client_secret", "my-secret");
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION)).andExpect(content().formData(form))
                .andRespond(withSuccess(tokenJson("t1", 7200), MediaType.APPLICATION_JSON));

        assertThat(provider.token()).isEqualTo("t1");

        server.verify();
    }

    @Test
    void fetchesOnceAndReusesTheToken() {
        expectToken("t1", 7200);

        assertThat(provider.token()).isEqualTo("t1");
        assertThat(provider.token()).isEqualTo("t1");

        server.verify(); // a second request would have failed: only one was expected
    }

    @Test
    void renewsFiveMinutesBeforeExpiry() {
        expectToken("t1", 7200);
        expectToken("t2", 7200);
        provider.token();

        clock.advance(Duration.ofSeconds(6850));
        assertThat(provider.token()).isEqualTo("t1");

        clock.advance(Duration.ofSeconds(100));
        assertThat(provider.token()).isEqualTo("t2");
    }

    @Test
    void invalidateForcesANewToken() {
        expectToken("t1", 7200);
        expectToken("t2", 7200);
        provider.token();

        provider.invalidate();

        assertThat(provider.token()).isEqualTo("t2");
    }

    @Test
    void retriesTechnicalFailuresWithGrowingPauses() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        server.expect(requestTo(URL)).andRespond(withServerError());
        expectToken("t1", 7200);

        assertThat(provider.token()).isEqualTo("t1");

        assertThat(pauses).containsExactly(Duration.ofMillis(500), Duration.ofMillis(1000));
        server.verify();
    }

    @Test
    void aDroppedConnectionIsRetried() {
        server.expect(requestTo(URL)).andRespond(
                MockRestResponseCreators
                        .withException(new IOException("connection reset")));
        expectToken("t1", 7200);

        assertThat(provider.token()).isEqualTo("t1");

        assertThat(pauses).containsExactly(Duration.ofMillis(500));
    }

    @Test
    void givesUpAfterTheRetries() {
        server.expect(ExpectedCount.times(3), requestTo(URL))
                .andRespond(withServerError());

        assertThatThrownBy(provider::token).isInstanceOfSatisfying(TemporarilyUnavailableException.class, ex -> {
            assertThat(ex.service()).isEqualTo("Bank API gateway");
            assertThat(ex.getCause()).isInstanceOf(OAuth2AuthorizationException.class);
        });

        server.verify();
    }

    @Test
    void invalidClientIsNotRetried() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_client\"}"));

        assertThatThrownBy(provider::token).isInstanceOfSatisfying(TemporarilyUnavailableException.class,
                ex -> assertThat(((OAuth2AuthorizationException) ex.getCause()).getError().getErrorCode())
                        .isEqualTo("invalid_client"));

        server.verify(); // exactly one request
        assertThat(pauses).isEmpty();
    }

    @Test
    void aFailedRequestDoesNotPoisonTheNextOne() {
        server.expect(requestTo(URL)).andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid_client\"}"));
        expectToken("t1", 7200);

        assertThatThrownBy(provider::token).isInstanceOf(TemporarilyUnavailableException.class);

        assertThat(provider.token()).isEqualTo("t1");
    }

    @Test
    void manyCallersAtOnceSendOneRequest() throws Exception {
        expectToken("shared", 7200);
        int callers = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return provider.token();
                }));
            }
            start.countDown();
            for (Future<String> result : results) {
                assertThat(result.get()).isEqualTo("shared");
            }
        } finally {
            pool.shutdownNow();
        }

        server.verify(); // one expected request, and no unexpected second one
    }

    // --- cases the stubbed endpoint cannot produce: a mocked OAuth2 client manager ---

    @Test
    void anEmptyAuthorizationResultIsRetriedThenRefused() {
        var manager = mock(OAuth2AuthorizedClientManager.class);
        var service = mock(OAuth2AuthorizedClientService.class);
        when(manager.authorize(ArgumentMatchers.any())).thenReturn(null);
        var provider = new GatewayTokenProvider(manager, service, 1, BACKOFF, pauses::add);

        assertThatThrownBy(provider::token).isInstanceOf(TemporarilyUnavailableException.class);

        verify(manager, times(2)).authorize(ArgumentMatchers.any());
        assertThat(pauses).containsExactly(BACKOFF);
    }

    @Test
    void anInterruptedPauseStopsTheRetries() {
        var manager = mock(OAuth2AuthorizedClientManager.class);
        var service = mock(OAuth2AuthorizedClientService.class);
        var failure = new OAuth2AuthorizationException(new OAuth2Error("server_error"));
        when(manager.authorize(ArgumentMatchers.any())).thenThrow(failure);
        var provider = new GatewayTokenProvider(manager, service, 2, BACKOFF, d -> {
            throw new InterruptedException("stop");
        });

        try {
            assertThatThrownBy(provider::token).isInstanceOfSatisfying(TemporarilyUnavailableException.class,
                    ex -> assertThat(ex.getCause()).isSameAs(failure));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clear the flag for the other tests
        }
        verify(manager, times(1)).authorize(ArgumentMatchers.any());
    }

    @Test
    void invalidateRemovesTheStoredClient() {
        var manager = mock(OAuth2AuthorizedClientManager.class);
        var service = mock(OAuth2AuthorizedClientService.class);

        new GatewayTokenProvider(manager, service, 0, BACKOFF, pauses::add).invalidate();

        verify(service).removeAuthorizedClient("bank-gateway", "azs-core");
    }
}
