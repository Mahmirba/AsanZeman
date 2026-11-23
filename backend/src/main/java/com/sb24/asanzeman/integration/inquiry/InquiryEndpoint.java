package com.sb24.asanzeman.integration.inquiry;

import java.time.Duration;
import java.util.Map;

/**
 * {@code azs.integration.inquiry.<name>.*} (5.3, D4): where one inquiry lives, how long it may take, how often a
 * failed call is repeated, and the fixed request fields that differ per environment ({@code fields}).
 *
 * @param baseUrl overrides {@code azs.integration.inquiry.base-url} for services on another host
 */
public record InquiryEndpoint(String baseUrl, String path, Duration timeout, Integer retries,
        Map<String, String> fields) {

    public InquiryEndpoint {
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        retries = retries == null ? 1 : retries;
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    /** A fixed request field the service documents with one usual value: the configuration may change it. */
    public String field(String name, String usual) {
        String value = fields.get(name);
        return value == null || value.isBlank() ? usual : value;
    }

    /** A fixed request field, which the configuration must give: a missing one stops the startup. */
    public String field(String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("azs.integration.inquiry.<name>.fields." + name + " is not set");
        }
        return value;
    }
}
