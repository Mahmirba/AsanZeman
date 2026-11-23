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
    static final String SEND_OTP = "/api/v1/Sms/SendOtp";
    static final String OTP_VALIDATE = "/api/v1/Sms/OtpValidate";
    private static final Logger log = LoggerFactory.getLogger(NotifierClient.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private record Reply(int status, String body) {
    }

    private final RestClient restClient;
    private final String baseUrl;
    private final String user;
    private final String pass;
    private final String clientName;
    private final List<String> otpArgs;

    public NotifierClient(RestClient restClient, String baseUrl, String user, String pass, String clientName,
            List<String> otpArgs) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.user = user;
        this.pass = pass;
        this.clientName = clientName;
        this.otpArgs = otpArgs;
    }

    /**
     * @param number  one recipient, {@code 0989xxxxxxxxx}
     * @param message the full text
     * @return the {@code smsId} the SMS centre gave, for tracing
     */
    public String sendSimpleSms(String number, String message) {
        Reply reply = post(SEND_SIMPLE_SMS, Map.of("user", user, "pass", pass, "ClientName", clientName,
                "numbers", List.of(number), "message", message));
        failOnError(reply);
        return retval(reply.body(), "smsId");
    }

    /**
     * {@code SendOtp} (D34): the SMS centre makes the 4-digit code and sends it. Called at once, not through the outbox,
     * because the caller waits for the id.
     *
     * @param number the customer's mobile, {@code 09xxxxxxxxx} as stored
     * @return the {@code sId} that {@link #validateOtp} needs
     */
    public String sendOtp(String number) {
        Reply reply = post(SEND_OTP, Map.of("user", user, "pass", pass, "ClientName", clientName, "number", number,
                "args", otpArgs, "lang", "fa"));
        failOnError(reply);
        return retval(reply.body(), "sId");
    }

    /**
     * {@code OtpValidate} (D34).
     *
     * @return {@code false} for a wrong or used code (HTTP 410 or {@code isValid: false})
     */
    public boolean validateOtp(String number, String otpId, String code) {
        Reply reply = post(OTP_VALIDATE, Map.of("user", user, "pass", pass, "ClientName", clientName,
                "number", number, "OtpCode", code, "OtpId", otpId, "lang", "fa"));
        if (reply.status() == 410) {
            return false;
        }
        failOnError(reply);
        try {
            return JSON.readTree(reply.body()).path("isValid").asBoolean(false);
        } catch (JacksonException e) {
            log.warn("Notifier answered OtpValidate with a body that is not JSON: {}", reply.body());
            throw technical("Notifier answered OtpValidate without JSON");
        }
    }

    private Reply post(String path, Map<String, Object> body) {
        try {
            return restClient.post().uri(baseUrl + path).contentType(MediaType.APPLICATION_JSON)
                    .body(body).exchange((request, response) -> new Reply(response.getStatusCode().value(),
                            new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            throw new TemporarilyUnavailableException(SERVICE, e);
        }
    }

    private static void failOnError(Reply reply) {
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
    }

    /** The {@code field} of the first {@code retval} entry. */
    private static String retval(String body, String field) {
        try {
            JsonNode value = JSON.readTree(body).path("retval").path(0).path(field);
            if (!value.isMissingNode() && !value.isNull() && !value.asString().isBlank()) {
                return value.asString();
            }
        } catch (JacksonException e) {
            // not JSON at all: handled below
        }
        log.warn("Notifier answered 2xx without a {}: {}", field, body);
        throw technical("Notifier answered without a " + field);
    }

    private static TemporarilyUnavailableException technical(String why) {
        return new TemporarilyUnavailableException(SERVICE, new IllegalStateException(why));
    }
}
