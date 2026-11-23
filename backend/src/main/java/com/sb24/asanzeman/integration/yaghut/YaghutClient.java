package com.sb24.asanzeman.integration.yaghut;

import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Yaghut's REST calls (5.4, D35). Yaghut is session-based: {@code loginStatic} returns a {@code JSESSIONID} cookie,
 * and that cookie is the only thing sent on later calls. Every call takes the session of whoever the call is for:
 * the technical account's, or an agency or customer's own.
 *
 * <p>Faults arrive as {@code {"exception": "...", "success": false}} and are matched on {@code exception}; any
 * {@code success:false} is a fault whatever its HTTP status. Known faults become {@link YaghutException}; everything
 * else (timeouts, 5xx, a proxy's HTML page, unknown faults, a missing cookie) becomes
 * {@link TemporarilyUnavailableException}, never a 400. Raw error bodies are logged in full (D22); request bodies are
 * not, because the login carries a password.
 */
public class YaghutClient {

    static final String SERVICE = "Yaghut";
    private static final String SESSION_COOKIE = "JSESSIONID";
    private static final Logger log = LoggerFactory.getLogger(YaghutClient.class);
    /** Decimals are read exactly: a balance must never pass through a double. */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private record Reply(int status, String body, List<String> setCookies) {
    }

    private final RestClient restClient;
    private final String baseUrl;

    public YaghutClient(RestClient restClient, String baseUrl) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
    }

    /**
     * {@code POST /user/loginStatic}.
     *
     * @throws YaghutException for {@code InvalidCredentialsWSException} and {@code SuspectedCustomerWSException}
     * @throws TemporarilyUnavailableException on a technical failure, or when no {@code JSESSIONID} comes back (the
     *         old session is never reused instead)
     */
    public YaghutLogin login(String username, String password) {
        Reply reply = post("/user/loginStatic", Map.of("username", username, "password", password), null);
        Map<String, Object> result = result(reply);
        String jsessionId = jsessionId(reply.setCookies());
        if (jsessionId == null) {
            log.error("Yaghut loginStatic answered without a JSESSIONID cookie");
            throw technical(new IllegalStateException("loginStatic returned no JSESSIONID"));
        }
        YaghutSession session = new YaghutSession(jsessionId, date(text(result, "sessionExpirationDate")));
        Object passwordExpiration = result.get("passwordExpirationBean");
        return new YaghutLogin(session, text(result, "name"), text(result, "cif"), text(result, "code"),
                text(result, "temporarySessionId"), text(result, "temporaryCause"),
                passwordExpiration == null ? null : JSON.writeValueAsString(passwordExpiration));
    }

    /** {@code POST /branches}: one page; the caller pages until {@code totalRecord} is reached. */
    public YaghutBranchPage branches(YaghutSession session, int offset, int length) {
        Map<String, Object> result = result(post("/branches", Map.of("offset", offset, "length", length), session));
        List<YaghutBranchPage.Branch> branches = new ArrayList<>();
        if (result.get("branches") instanceof List<?> items) {
            for (Object item : items) {
                Map<String, Object> b = asMap(item);
                branches.add(new YaghutBranchPage.Branch(text(b, "branchCode"), text(b, "name"), text(b, "city"),
                        text(b, "address"), text(b, "tel"), text(b, "fax"), text(b, "zipCode")));
            }
        }
        if (!(result.get("totalRecord") instanceof Number total)) {
            // never read a malformed answer as "no branches": BranchSyncJob would deactivate every branch (4.4)
            log.warn("Yaghut branches answered without totalRecord: {}", result);
            throw technical(new IllegalStateException("branches answer has no totalRecord"));
        }
        return new YaghutBranchPage(total.longValue(), branches);
    }

    /**
     * {@code POST /customerDetailInfo} with the national code ({@code code}); exactly one of {@code cif} or
     * {@code code} may be sent, and we always send {@code code}. Uses the caller's own session (D35).
     *
     * @throws YaghutException {@code InvalidCustomerWSException} (not a bank customer) or
     *         {@code InvalidSessionWSException} (the session has ended)
     * @throws TemporarilyUnavailableException on a technical failure or an unreadable birth date
     */
    public YaghutCustomer customerDetailInfo(YaghutSession session, String nationalCode) {
        Map<String, Object> r = result(post("/customerDetailInfo", Map.of("code", nationalCode), session));
        return new YaghutCustomer(text(r, "cif"), text(r, "code"), text(r, "firstName"), text(r, "lastName"),
                fullName(r), text(r, "fatherName"), birthDate(text(r, "birthDate")), text(r, "mobile"),
                text(r, "ssn"), text(r, "certificateSerial"), text(r, "certificateSeries"),
                text(r, "personalityType"), text(r, "gender"));
    }

    /**
     * {@code POST /deposit}: the customer's open deposits of a natural person who owns and signs, with the withdrawable
     * balance, with the customer's own session. The filters are asked for here and checked again by the caller.
     * The list is read from {@code result.deposits}, or from {@code result} itself when that is the list.
     *
     * @throws YaghutException {@code InvalidSessionWSException} or {@code InvalidCustomerWSException}
     */
    public List<YaghutDeposit> deposits(YaghutSession session, String cif) {
        Map<String, Object> body = Map.of("cif", cif, "depositStatus", "OPEN", "needWithdrawableBalance", true,
                "personality", List.of("ACTUAL"), "signatureStatus", List.of("OWNER_OF_DEPOSIT_AND_SIGNATURE"));
        Object result = rawResult(post("/deposit", body, session));
        Object list = result instanceof List<?> ? result : asMap(result).get("deposits");
        List<YaghutDeposit> deposits = new ArrayList<>();
        if (list instanceof List<?> items) {
            for (Object item : items) {
                Map<String, Object> d = asMap(item);
                deposits.add(new YaghutDeposit(text(d, "depositNumber"), text(d, "group"), number(d, "depositType"),
                        text(d, "owner"), text(d, "signature"), text(d, "depositStatus"), text(d, "currency"),
                        text(d, "personality"), decimal(d, "availableBalance")));
            }
        }
        return deposits;
    }

    private static Long number(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Number n ? n.longValue() : null;
    }

    private static java.math.BigDecimal decimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        try {
            return value == null ? null : new java.math.BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Yaghut {} is not a number: {}", key, value);
            return null;
        }
    }

    /**
     * Yaghut gives {@code firstName} and {@code lastName} (and {@code title}), not a full name: the full name is the
     * two put together, or the {@code title} when there is no first or last name (a company).
     */
    private static String fullName(Map<String, Object> customer) {
        String name = ((text(customer, "firstName") == null ? "" : text(customer, "firstName")) + " "
                + (text(customer, "lastName") == null ? "" : text(customer, "lastName"))).trim();
        if (!name.isEmpty()) {
            return name;
        }
        return text(customer, "title");
    }

    private static java.time.LocalDate birthDate(String value) {
        try {
            return YaghutDates.parseDate(value);
        } catch (IllegalArgumentException e) {
            log.warn("Yaghut birthDate is unreadable: {}", value);
            throw technical(e);
        }
    }

    // ---- transport ---------------------------------------------------------------------------------------------

    private Reply post(String path, Object body, YaghutSession session) {
        try {
            return restClient.post().uri(baseUrl + path).contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> {
                        if (session != null) {
                            headers.set(HttpHeaders.COOKIE, SESSION_COOKIE + "=" + session.jsessionId());
                        }
                    }).body(body)
                    .exchange((request, response) -> new Reply(response.getStatusCode().value(),
                            new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8),
                            response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)));
        } catch (RestClientException e) {
            throw technical(e);
        }
    }

    /** The {@code result} object of a successful answer, or the faults described in the class comment. */
    private Map<String, Object> result(Reply reply) {
        Object result = rawResult(reply);
        return result instanceof Map<?, ?> ? asMap(result) : Map.of();
    }

    /** The {@code result} of a successful answer, whatever its shape: an object, or a list. */
    private Object rawResult(Reply reply) {
        Map<String, Object> json = parse(reply);
        if (Boolean.FALSE.equals(json.get("success"))) {
            throw fault(json, reply);
        }
        if (reply.status() >= 400) {
            log.warn("Yaghut answered HTTP {}: {}", reply.status(), reply.body());
            throw technical(new IllegalStateException("Yaghut answered HTTP " + reply.status()));
        }
        return json.containsKey("result") ? json.get("result") : json;
    }

    private Map<String, Object> parse(Reply reply) {
        if (reply.body().isBlank()) {
            log.warn("Yaghut answered HTTP {} with an empty body", reply.status());
            throw technical(new IllegalStateException("empty answer, HTTP " + reply.status()));
        }
        try {
            if (JSON.readValue(reply.body(), Object.class) instanceof Map<?, ?> map) {
                return asMap(map);
            }
        } catch (JacksonException e) {
            // not JSON at all, e.g. a proxy's HTML page: handled below
        }
        log.warn("Yaghut answered HTTP {} with a body that is not a JSON object: {}", reply.status(), reply.body());
        throw technical(new IllegalStateException("unreadable answer, HTTP " + reply.status()));
    }

    private RuntimeException fault(Map<String, Object> json, Reply reply) {
        String name = text(json, "exception");
        String message = text(json, "exception_message");
        YaghutException.Fault fault = YaghutException.Fault.from(name);
        if (fault == null) {
            // WebserviceException or a fault we do not know yet: technical, and the full body shows up in the logs
            log.warn("Yaghut fault {} (HTTP {}): {}", name, reply.status(), reply.body());
            return technical(new IllegalStateException("Yaghut fault " + name));
        }
        if (fault == YaghutException.Fault.SERVICE_ACCESS_DENIED) {
            // ops alert: the message names the service and channel IDs that are not active
            log.error("Yaghut denies access to the service, not retried: {}", message);
        }
        return new YaghutException(fault, message);
    }

    private static TemporarilyUnavailableException technical(Throwable cause) {
        return new TemporarilyUnavailableException(SERVICE, cause);
    }

    // ---- reading values ----------------------------------------------------------------------------------------

    /** Searches <em>all</em> {@code Set-Cookie} headers; {@code JSESSIONID} need not be the first. */
    static String jsessionId(List<String> setCookies) {
        for (String header : setCookies) {
            String cookie = header.split(";", 2)[0].trim();
            if (cookie.startsWith(SESSION_COOKIE + "=")) {
                String value = cookie.substring(SESSION_COOKIE.length() + 1).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Instant date(String value) {
        try {
            return YaghutDates.parse(value);
        } catch (IllegalArgumentException e) {
            log.warn("Yaghut sessionExpirationDate is unreadable: {}", value);
            throw technical(e);
        }
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
