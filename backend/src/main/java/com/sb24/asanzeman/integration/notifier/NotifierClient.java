package com.sb24.asanzeman.integration.notifier;

import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The SMS centre's {@code SendSimpleSms} (6.4, D40). The credentials ({@code user}, {@code pass},
 * {@code ClientName}) go in the body and are never logged. The caller sends the number as {@code 0989xxxxxxxxx}.
 *
 * <p>400 is {@link NotifierRejectedException} (never retried), 403 is {@link NotifierCredentialsException}, and
 * everything else that is not a clean answer with a {@code smsId} (500, timeout, network error, a body that is not
 * the expected JSON) is {@link TemporarilyUnavailableException}.
 */
public class NotifierClient {

    static final String SERVICE = "Notifier";
    static final String SEND_SIMPLE_SMS = "/api/v1/sms/SendSimpleSms";
    private static final Logger log = LoggerFactory.getLogger(NotifierClient.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private record Reply(int status, String body) {
    }

    private final RestClient restClient;
    private final String baseUrl;
    private final String user;
    private final String pass;
    private final String clientName;

    public NotifierClient(RestClient restClient, String baseUrl, String user, String pass, String clientName) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.user = user;
        this.pass = pass;
        this.clientName = clientName;
    }

    /**
     * @param number  one recipient, {@code 0989xxxxxxxxx}
     * @param message the full text
     * @return the {@code smsId} the SMS centre gave, for tracing
     */
    public String sendSimpleSms(String number, String message) {
        Map<String, Object> body = Map.of("user", user, "pass", pass, "ClientName", clientName,
                "numbers", List.of(number), "message", message);
        Reply reply;
        try {
            reply = restClient.post().uri(baseUrl + SEND_SIMPLE_SMS).contentType(MediaType.APPLICATION_JSON)
                    .body(body).exchange((request, response) -> new Reply(response.getStatusCode().value(),
                            new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            throw new TemporarilyUnavailableException(SERVICE, e);
        }
        if (reply.status() == 400) {
            log.error("Notifier refused the request (400), not retried: {}", reply.body());
            throw new NotifierRejectedException(reply.body());
        }
        if (reply.status() == 403) {
            log.error("Notifier refused our credentials (403), check azs.integration.notifier: {}", reply.body());
            throw new NotifierCredentialsException(reply.body());
        }
        if (reply.status() < 200 || reply.status() >= 300) {
            log.warn("Notifier answered HTTP {}: {}", reply.status(), reply.body());
            throw technical("Notifier answered HTTP " + reply.status());
        }
        return smsId(reply.body());
    }

    private static String smsId(String body) {
        try {
            JsonNode first = JSON.readTree(body).path("retval").path(0);
            JsonNode smsId = first.path("smsId");
            if (!smsId.isMissingNode() && !smsId.isNull() && !smsId.asString().isBlank()) {
                return smsId.asString();
            }
        } catch (JacksonException e) {
            // not JSON at all: handled below
        }
        log.warn("Notifier answered 2xx without a smsId: {}", body);
        throw technical("Notifier answered without a smsId");
    }

    private static TemporarilyUnavailableException technical(String why) {
        return new TemporarilyUnavailableException(SERVICE, new IllegalStateException(why));
    }
}
