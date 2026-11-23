package com.sb24.asanzeman.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The OpenAPI contract is updated first (section 9), so it must at least be valid YAML and describe the agency panel
 * calls that exist. Run from the {@code backend} directory, as Maven does.
 */
class OpenApiContractTest {

    private static Map<String, Object> contract;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void load() throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of("..", "docs", "api", "openapi.yaml"),
                StandardCharsets.UTF_8)) {
            contract = new Yaml().load(reader);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> paths() {
        return (Map<String, Object>) contract.get("paths");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> schemas() {
        return (Map<String, Object>) ((Map<String, Object>) contract.get("components")).get("schemas");
    }

    @Test
    void theAgencyPanelCallsAreDescribed() {
        assertThat(paths()).containsKeys("/auth/bank/login", "/auth/agency/login", "/agency/requests",
                "/agency/requests/export", "/agency/requests/{id}", "/agency/requests/{id}/cancel",
                "/agency/profile", "/agency/branches", "/agency/customers/lookup", "/auth/customer/login",
                "/auth/customer/otp/verify", "/customer/requests", "/customer/requests/{id}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theRequestsPathHasBothRegisterAndList() {
        Map<String, Object> requests = (Map<String, Object>) paths().get("/agency/requests");

        assertThat(requests).containsKeys("post", "get");
    }

    @Test
    void everySchemaTheAgencyCallsUseIsDefined() {
        assertThat(schemas()).containsKeys("RegisterRequestBody", "RegisteredRequest", "RequestSummary", "RequestPage",
                "RequestDetails", "CancelResult", "TokenResponse");
    }
}
