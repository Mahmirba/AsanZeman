package com.sb24.asanzeman.integration.arcaptcha;

import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@code POST /api/arcaptcha/api/verify} with {@code {challenge_id, site_key, secret_key}} (6.5). It does no retry:
 * a token is valid for one use, and a second call would only turn a success into {@code timeout-or-duplicate}.
 */
public class ArcaptchaClient {

    static final String SERVICE = "ARcaptcha";
    static final String BAD_REQUEST = "bad-request";

    private final RestClient restClient;
    private final String verifyUrl;

    public ArcaptchaClient(RestClient restClient, String verifyUrl) {
        this.restClient = restClient;
        this.verifyUrl = verifyUrl;
    }

    /**
     * @return ARcaptcha's answer; an HTTP 4xx is reported as {@code success=false} with its codes, or with
     *         {@code bad-request} when the body has none
     * @throws TemporarilyUnavailableException on timeout, network error, 5xx or an empty answer
     */
    public ArcaptchaVerifyResponse verify(String siteKey, String secretKey, String token) {
        Map<String, String> body = Map.of("challenge_id", token, "site_key", siteKey, "secret_key", secretKey);
        try {
            ArcaptchaVerifyResponse response = restClient.post().uri(verifyUrl).contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(ArcaptchaVerifyResponse.class);
            if (response == null) {
                throw new TemporarilyUnavailableException(SERVICE, new IllegalStateException("empty response"));
            }
            return response;
        } catch (HttpClientErrorException e) {
            return fromClientError(e);
        } catch (RestClientException e) {
            throw new TemporarilyUnavailableException(SERVICE, e);
        }
    }

    private static ArcaptchaVerifyResponse fromClientError(HttpClientErrorException e) {
        try {
            ArcaptchaVerifyResponse parsed = e.getResponseBodyAs(ArcaptchaVerifyResponse.class);
            if (parsed != null && !parsed.errorCodes().isEmpty()) {
                return new ArcaptchaVerifyResponse(false, parsed.errorCodes());
            }
        } catch (RuntimeException ignored) {
            // unreadable body: treated as a request problem below
        }
        return new ArcaptchaVerifyResponse(false, List.of(BAD_REQUEST));
    }
}
