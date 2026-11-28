package com.sb24.asanzeman.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.support.OracleTestContainer;
import com.sb24.asanzeman.support.TestKeys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Phase 0 exit criterion: the core starts on Oracle and the health endpoint answers without a token on the management
 * port (not on the API port, see docs/ops-settings.md).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(locations = "classpath:application-test.properties", properties = "management.server.port=0")
class HealthEndpointIT {

    @DynamicPropertySource
    static void oracle(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", OracleTestContainer::jdbcUrl);
        registry.add("spring.datasource.username", () -> "azs_app");
        registry.add("spring.datasource.password", () -> "test");
        TestKeys.register(registry);
    }

    @Autowired
    TestRestTemplate rest;

    @LocalManagementPort
    int managementPort;

    @Test
    void healthIsUpAndOpenOnTheManagementPort() {
        var response = rest.getForEntity("http://localhost:" + managementPort + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(response.getHeaders().getFirst("Set-Cookie")).isNull();
    }

    @Test
    void theApiPortDoesNotServeTheActuator() {
        var response = rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anyOtherPathNeedsAToken() {
        var response = rest.getForEntity("/api/branch/requests", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
    }
}
