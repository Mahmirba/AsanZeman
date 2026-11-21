package com.sb24.asanzeman.shared.config;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import com.sb24.asanzeman.auth.infrastructure.security.JwtSupport;
import com.sb24.asanzeman.auth.infrastructure.security.NimbusAccessTokenIssuer;
import com.sb24.asanzeman.shared.web.ProblemDetailExceptionHandler;
import com.sb24.asanzeman.shared.web.ProblemDetailSecurityHandlers;
import com.sb24.asanzeman.support.TestKeys;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The real security chain with real RS256 tokens: 401 without a token, the {@code role} claim becomes a role, a token
 * revoked at logout is refused, and the login and refresh paths stay public.
 */
@WebMvcTest(controllers = SecurityConfigurationTest.ProbeController.class)
@Import({SecurityConfigurationTest.ProbeController.class, SecurityConfigurationTest.Decoder.class,
        ProblemDetailExceptionHandler.class, ProblemDetailSecurityHandlers.class, SecurityConfiguration.class})
class SecurityConfigurationTest {

    private static final String ISSUER = "asanzeman";
    private static final String PRIVATE = TestKeys.pem("PRIVATE KEY", TestKeys.pair().getPrivate().getEncoded());
    private static final String PUBLIC = TestKeys.pem("PUBLIC KEY", TestKeys.pair().getPublic().getEncoded());

    @RestController
    static class ProbeController {

        @GetMapping("/api/probe/branch")
        @PreAuthorize("hasRole('BRANCH')")
        String branch() {
            return "branch";
        }

        @GetMapping("/api/probe/admin")
        @PreAuthorize("hasRole('ADMIN')")
        String admin() {
            return "admin";
        }
    }

    @TestConfiguration
    static class Decoder {

        @Bean
        JwtDecoder jwtDecoder(JwtBlacklistPort blacklist) {
            return JwtSupport.decoder(PUBLIC, ISSUER, blacklist);
        }
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JwtBlacklistPort blacklist;

    private static String token(Role role, String jti, Duration age, Duration lifetime, String issuer) {
        Instant issuedAt = Instant.now().minus(age);
        var session = new Session(UUID.randomUUID(), "ali", role, role == Role.AGENCY ? null : "0123");
        return new NimbusAccessTokenIssuer(JwtSupport.encoder(PRIVATE, PUBLIC), issuer).issue(session, jti, issuedAt,
                issuedAt.plus(lifetime));
    }

    private static String bearer(Role role) {
        return "Bearer " + token(role, UUID.randomUUID().toString(), Duration.ZERO, Duration.ofMinutes(15), ISSUER);
    }

    @Test
    void noTokenIs401Problem() throws Exception {
        mvc.perform(get("/api/probe/branch")).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"));
    }

    @Test
    void aBranchTokenReachesABranchEndpoint() throws Exception {
        mvc.perform(get("/api/probe/branch").header("Authorization", bearer(Role.BRANCH)))
                .andExpect(status().isOk()).andExpect(content().string("branch"));
    }

    @Test
    void aBranchTokenIsForbiddenOnAnAdminEndpointWithAProblemBody() throws Exception {
        mvc.perform(get("/api/probe/admin").header("Authorization", bearer(Role.BRANCH)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("access.denied"));
    }

    @Test
    void anAdminTokenReachesAnAdminEndpoint() throws Exception {
        mvc.perform(get("/api/probe/admin").header("Authorization", bearer(Role.ADMIN)))
                .andExpect(status().isOk()).andExpect(content().string("admin"));
    }

    @Test
    void aTokenRevokedAtLogoutIs401() throws Exception {
        when(blacklist.isRevoked("gone")).thenReturn(true);
        String revoked = "Bearer " + token(Role.BRANCH, "gone", Duration.ZERO, Duration.ofMinutes(15), ISSUER);

        mvc.perform(get("/api/probe/branch").header("Authorization", revoked)).andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void anExpiredTokenIs401() throws Exception {
        String expired = "Bearer " + token(Role.BRANCH, "e", Duration.ofHours(2), Duration.ofHours(1), ISSUER);

        mvc.perform(get("/api/probe/branch").header("Authorization", expired)).andExpect(status().isUnauthorized());
    }

    @Test
    void aTokenFromAnotherIssuerIs401() throws Exception {
        String foreign = "Bearer " + token(Role.BRANCH, "f", Duration.ZERO, Duration.ofMinutes(15), "other");

        mvc.perform(get("/api/probe/branch").header("Authorization", foreign)).andExpect(status().isUnauthorized());
    }

    @Test
    void aMalformedTokenIs401() throws Exception {
        mvc.perform(get("/api/probe/branch").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theLoginAndRefreshPathsArePublic() throws Exception {
        // no controller is mounted in this slice, so the answer is 404: the point is that it is not 401
        mvc.perform(post("/api/auth/bank/login")).andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isNotFound());
    }

    @Test
    void logoutNeedsAToken() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }
}
