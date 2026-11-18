package com.sb24.asanzeman.integration.personnel;

import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code POST .../GetOverallPersonelInfo} through the bank API gateway (6.2). The {@code RestClient} carries the
 * gateway Bearer interceptor, which also repeats a call once after a 401.
 *
 * <p>HTTP 404 («اطلاعات یافت نشد») means the user is unknown and gives an empty result. Everything else that is not a
 * usable answer (5xx, timeout, a second 401, a broken body, no branch) is {@link TemporarilyUnavailableException}:
 * nobody is let in without a branch.
 */
public class PersonnelInfoClient {

    static final String SERVICE = "personnelInfo";
    private static final Logger log = LoggerFactory.getLogger(PersonnelInfoClient.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private record Reply(int status, String body) {
    }

    private final RestClient restClient;
    private final String url;

    public PersonnelInfoClient(RestClient restClient, String url) {
        this.restClient = restClient;
        this.url = url;
    }

    /** @param accountName the login name without the {@code @sb24.ir} suffix */
    public Optional<PersonnelInfo> find(String accountName) {
        Reply reply = post(Map.of("cfcifNO", 0, "accountName", accountName, "personelCode", 0));
        if (reply.status() == 404) {
            return Optional.empty();
        }
        if (reply.status() < 200 || reply.status() >= 300) {
            log.warn("personnelInfo answered HTTP {}: {}", reply.status(), reply.body());
            throw technical(new IllegalStateException("personnelInfo answered HTTP " + reply.status()));
        }
        Map<String, Object> user = asMap(parse(reply).get("userInfo"));
        String branchCode = branchCode(user.get("abrnchcod"));
        if (branchCode == null) {
            log.warn("personnelInfo answered without a usable userInfo.abrnchcod: {}", reply.body());
            throw technical(new IllegalStateException("personnelInfo gave no branch"));
        }
        return Optional.of(new PersonnelInfo(branchCode, text(user, "prsnCode"), text(user, "personelUserName"),
                text(user, "aBranchName"), text(user, "position"), text(user, "job")));
    }

    private Reply post(Object body) {
        try {
            return restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                    .exchange((request, response) -> new Reply(response.getStatusCode().value(),
                            new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (RestClientException e) {
            throw technical(e);
        }
    }

    private static Map<String, Object> parse(Reply reply) {
        try {
            if (JSON.readValue(reply.body(), Object.class) instanceof Map<?, ?> map) {
                return asMap(map);
            }
        } catch (JacksonException e) {
            // not JSON: handled below
        }
        log.warn("personnelInfo answered HTTP {} with a body that is not a JSON object: {}", reply.status(),
                reply.body());
        throw technical(new IllegalStateException("unreadable personnelInfo answer"));
    }

    /** The numeric {@code abrnchcod} becomes a string with no zero stripping; anything else is unusable (D32). */
    private static String branchCode(Object value) {
        if (value instanceof Number n && n.doubleValue() == n.longValue()) {
            return String.valueOf(n.longValue());
        }
        if (value instanceof String s && !s.isBlank()) {
            return s.trim();
        }
        return null;
    }

    private static TemporarilyUnavailableException technical(Throwable cause) {
        return new TemporarilyUnavailableException(SERVICE, cause);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? null : value.toString();
    }
}
