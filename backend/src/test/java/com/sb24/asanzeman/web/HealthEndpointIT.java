package com.sb24.asanzeman.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.support.OracleTestContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/** Phase 0 exit criterion: the core starts on Oracle and the health endpoint answers without a token. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(locations = "classpath:application-test.properties")
class HealthEndpointIT {

    @DynamicPropertySource
    static void oracle(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", OracleTestContainer::jdbcUrl);
        registry.add("spring.datasource.username", () -> "azs_app");
        registry.add("spring.datasource.password", () -> "test");
    }

    @Autowired
    TestRestTemplate rest;

    @Test
    void healthIsUpAndPublic() {
        var response = rest.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(response.getHeaders().getFirst("Set-Cookie")).isNull();
    }

    @Test
    void anyOtherPathNeedsAToken() {
        var response = rest.getForEntity("/api/branch/requests", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("application/problem+json");
    }
}
