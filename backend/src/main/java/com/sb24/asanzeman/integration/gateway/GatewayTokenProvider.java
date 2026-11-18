package com.sb24.asanzeman.integration.gateway;

import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.web.client.RestClient;

/**
 * The gateway token (5.2, D28), obtained through Spring Security's OAuth2 client: it sends the client-credentials
 * request, keeps the token in memory and renews it {@code refreshBefore} the expiry. This class only adds what the
 * library does not do: one request at a time (a {@link ReentrantLock}, which suits virtual threads), retries with
 * backoff for technical failures, and no retry when the gateway refuses our credentials.
 */
public class GatewayTokenProvider {

    /** Pauses between retries; replaceable so tests need not wait. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    public static final String REGISTRATION_ID = "bank-gateway";
    static final String PRINCIPAL_NAME = "azs-core";
    static final String SERVICE = "Bank API gateway";
    /** Errors that retrying cannot fix: our client id, secret or request is wrong (an ops problem). */
    static final Set<String> CONFIGURATION_ERRORS = Set.of(OAuth2ErrorCodes.INVALID_CLIENT,
            OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ErrorCodes.INVALID_GRANT, OAuth2ErrorCodes.UNAUTHORIZED_CLIENT,
            OAuth2ErrorCodes.UNSUPPORTED_GRANT_TYPE, OAuth2ErrorCodes.INVALID_SCOPE);

    private static final Logger log = LoggerFactory.getLogger(GatewayTokenProvider.class);

    private final OAuth2AuthorizedClientManager manager;
    private final OAuth2AuthorizedClientService clients;
    private final int retries;
    private final Duration backoff;
    private final Sleeper sleeper;
    private final ReentrantLock lock = new ReentrantLock();
    private final OAuth2AuthorizeRequest authorizeRequest;

    GatewayTokenProvider(OAuth2AuthorizedClientManager manager, OAuth2AuthorizedClientService clients, int retries,
            Duration backoff, Sleeper sleeper) {
        this.manager = manager;
        this.clients = clients;
        this.retries = retries;
        this.backoff = backoff;
        this.sleeper = sleeper;
        Authentication principal = new AnonymousAuthenticationToken("azs-core-key", PRINCIPAL_NAME,
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        this.authorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
                .principal(principal).build();
    }

    /**
     * @param tokenRestClient the client for the token endpoint; build it with
     *                        {@link GatewayTokenRestClients#configure(RestClient.Builder)}
     * @param refreshBefore   how long before the expiry a new token is requested
     */
    public static GatewayTokenProvider create(String tokenUrl, String clientId, String clientSecret,
            RestClient tokenRestClient, Duration refreshBefore, Clock clock, int retries, Duration backoff,
            Sleeper sleeper) {
        // the gateway expects client_id and client_secret in the form body, not in a Basic header
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(clientId).clientSecret(clientSecret)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .tokenUri(tokenUrl).build();
        var repository = new InMemoryClientRegistrationRepository(registration);
        var clients = new InMemoryOAuth2AuthorizedClientService(repository);
        var tokenResponseClient = new RestClientClientCredentialsTokenResponseClient();
        tokenResponseClient.setRestClient(tokenRestClient);
        var provider = new ClientCredentialsOAuth2AuthorizedClientProvider();
        provider.setAccessTokenResponseClient(tokenResponseClient);
        provider.setClockSkew(refreshBefore);
        provider.setClock(clock);
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(repository, clients);
        manager.setAuthorizedClientProvider(provider);
        return new GatewayTokenProvider(manager, clients, retries, backoff, sleeper);
    }

    /** @throws TemporarilyUnavailableException when the gateway cannot give a token (after the retries) */
    public String token() {
        lock.lock();
        try {
            return request();
        } finally {
            lock.unlock();
        }
    }

    /** Forgets the token, e.g. after an inquiry answered 401, so the next call asks for a new one. */
    public void invalidate() {
        clients.removeAuthorizedClient(REGISTRATION_ID, PRINCIPAL_NAME);
    }

    private String request() {
        OAuth2AuthorizationException last = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            if (attempt > 0) {
                pause(attempt, last);
            }
            try {
                return accessToken();
            } catch (OAuth2AuthorizationException e) {
                last = e;
                if (CONFIGURATION_ERRORS.contains(e.getError().getErrorCode())) {
                    // invalid_client and friends: never retried, so a wrong secret cannot lock the account (ops alert)
                    log.error("gateway token request refused, check client_id and client_secret: {}", e.getMessage());
                    throw new TemporarilyUnavailableException(SERVICE, e);
                }
                log.warn("gateway token request failed (attempt {} of {}): {}", attempt + 1, retries + 1,
                        e.getMessage());
            }
        }
        throw new TemporarilyUnavailableException(SERVICE, last);
    }

    private String accessToken() {
        OAuth2AuthorizedClient client = manager.authorize(authorizeRequest);
        if (client == null) {
            throw new OAuth2AuthorizationException(new OAuth2Error("invalid_token_response",
                    "the OAuth2 client returned no authorized client", null));
        }
        return client.getAccessToken().getTokenValue();
    }

    private void pause(int attempt, OAuth2AuthorizationException last) {
        try {
            sleeper.sleep(backoff.multipliedBy(attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TemporarilyUnavailableException(SERVICE, last);
        }
    }
}
