package com.sb24.asanzeman.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import com.sb24.asanzeman.support.TestKeys;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/** The real RS256 encoder and decoder, with the real validators. */
class JwtSupportTest {

    private static final String ISSUER = "asanzeman";
    private static final String PRIVATE = TestKeys.pem("PRIVATE KEY", TestKeys.pair().getPrivate().getEncoded());
    private static final String PUBLIC = TestKeys.pem("PUBLIC KEY", TestKeys.pair().getPublic().getEncoded());
    private static final Session BRANCH_USER = new Session(UUID.randomUUID(), "ali", Role.BRANCH, "0123");

    private JwtBlacklistPort blacklist;
    private JwtDecoder decoder;
    private NimbusAccessTokenIssuer issuer;

    @BeforeEach
    void setUp() {
        blacklist = mock(JwtBlacklistPort.class);
        decoder = JwtSupport.decoder(PUBLIC, ISSUER, blacklist);
        issuer = new NimbusAccessTokenIssuer(JwtSupport.encoder(PRIVATE, PUBLIC), ISSUER);
    }

    private String token(Session session, String jti, Duration age, Duration lifetime) {
        Instant issuedAt = Instant.now().minus(age);
        return issuer.issue(session, jti, issuedAt, issuedAt.plus(lifetime));
    }

    @Test
    void aTokenCarriesTheDocumentedClaimsAndIsRs256() {
        String token = token(BRANCH_USER, "jti-1", Duration.ZERO, Duration.ofMinutes(15));

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("kid", "azs-jwt-1");
        assertThat(jwt.<String>getClaim("iss")).isEqualTo(ISSUER);
        assertThat(jwt.getSubject()).isEqualTo("ali");
        assertThat(jwt.getId()).isEqualTo("jti-1");
        assertThat(jwt.<String>getClaim("role")).isEqualTo("BRANCH");
        assertThat(jwt.<String>getClaim("branch_code")).isEqualTo("0123");
        assertThat(jwt.<String>getClaim("uid")).isEqualTo(BRANCH_USER.subjectId().toString());
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void aSessionWithoutABranchHasNoBranchClaim() {
        var agency = new Session(UUID.randomUUID(), "agency", Role.AGENCY, null);

        Jwt jwt = decoder.decode(token(agency, "jti-2", Duration.ZERO, Duration.ofMinutes(15)));

        assertThat(jwt.hasClaim("branch_code")).isFalse();
        assertThat(jwt.<String>getClaim("role")).isEqualTo("AGENCY");
    }

    @Test
    void anExpiredTokenIsRejected() {
        String expired = token(BRANCH_USER, "jti-3", Duration.ofHours(2), Duration.ofHours(1));

        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void aTamperedTokenIsRejected() {
        String token = token(BRANCH_USER, "jti-4", Duration.ZERO, Duration.ofMinutes(15));
        String[] parts = token.split("\\.");
        // swap the payload for another one and keep the old signature
        String other = token(new Session(UUID.randomUUID(), "admin", Role.ADMIN, "0123"), "jti-4", Duration.ZERO,
                Duration.ofMinutes(15)).split("\\.")[1];
        String forged = parts[0] + "." + other + "." + parts[2];

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair other = generator.generateKeyPair();
        var foreign = new NimbusAccessTokenIssuer(JwtSupport.encoder(
                TestKeys.pem("PRIVATE KEY", other.getPrivate().getEncoded()),
                TestKeys.pem("PUBLIC KEY", other.getPublic().getEncoded())), ISSUER);
        String token = foreign.issue(BRANCH_USER, "jti-5", Instant.now(), Instant.now().plusSeconds(900));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected() {
        var other = new NimbusAccessTokenIssuer(JwtSupport.encoder(PRIVATE, PUBLIC), "someone-else");
        String token = other.issue(BRANCH_USER, "jti-6", Instant.now(), Instant.now().plusSeconds(900));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void aRevokedTokenIsRejected() {
        when(blacklist.isRevoked("jti-7")).thenReturn(true);
        String token = token(BRANCH_USER, "jti-7", Duration.ZERO, Duration.ofMinutes(15));

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class)
                .hasMessageContaining("revoked");
    }

    @Test
    void garbageIsRejected() {
        assertThatThrownBy(() -> decoder.decode("not.a.jwt")).isInstanceOf(JwtException.class);
    }
}
