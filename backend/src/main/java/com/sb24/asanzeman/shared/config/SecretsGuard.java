package com.sb24.asanzeman.shared.config;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * D13: the configuration file in git carries {@code CHANGE_ME} for every secret. A server whose
 * copy still holds that value must not start. Pure Java so it is unit-testable; the Spring hook
 * is {@link SecretsGuardConfiguration}.
 */
public final class SecretsGuard {

    public static final String PLACEHOLDER = "CHANGE_ME";

    private SecretsGuard() {
    }

    /** Returns the keys (sorted) whose value is still the placeholder; empty when all are set. */
    public static List<String> unsetSecrets(Map<String, String> properties) {
        var unset = new TreeMap<String, String>();
        properties.forEach((key, value) -> {
            if (value != null && value.trim().equalsIgnoreCase(PLACEHOLDER)) {
                unset.put(key, value);
            }
        });
        return List.copyOf(unset.keySet());
    }

    /** @throws IllegalStateException naming every unset secret, so ops can fix them in one go. */
    public static void verify(Map<String, String> properties) {
        var unset = unsetSecrets(properties);
        if (!unset.isEmpty()) {
            throw new IllegalStateException(
                    "Refusing to start: these configuration values are still " + PLACEHOLDER + ": "
                            + String.join(", ", unset));
        }
    }
}
