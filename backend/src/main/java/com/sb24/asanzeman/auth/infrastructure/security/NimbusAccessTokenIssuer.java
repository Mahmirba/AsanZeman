package com.sb24.asanzeman.auth.infrastructure.security;

import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.auth.domain.port.out.AccessTokenIssuerPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * RS256 access tokens (6): claims {@code iss}, {@code sub} (the account or agency name), {@code uid} (its id),
 * {@code role}, {@code sid} (the session), {@code branch_code} (bank users), {@code iat}, {@code exp} and {@code jti}.
 */
public class NimbusAccessTokenIssuer implements AccessTokenIssuerPort {

    public static final String ROLE_CLAIM = "role";
    public static final String BRANCH_CLAIM = "branch_code";
    public static final String USER_ID_CLAIM = "uid";
    /** The session family: finds the server-side data kept with the session, such as the Yaghut session. */
    public static final String SESSION_CLAIM = "sid";
    /** For an agency session, the Yaghut user who logged in for it. */
    public static final String ACTOR_CLAIM = "usr";
    public static final String KEY_ID = "azs-jwt-1";

    private final JwtEncoder encoder;
    private final String issuer;

    public NimbusAccessTokenIssuer(JwtEncoder encoder, String issuer) {
        this.encoder = encoder;
        this.issuer = issuer;
    }

    @Override
    public String issue(Session session, UUID familyId, String jti, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer(issuer).subject(session.subjectName())
                .issuedAt(issuedAt).expiresAt(expiresAt).id(jti).claim(USER_ID_CLAIM, session.subjectId().toString())
                .claim(ROLE_CLAIM, session.role().name()).claim(SESSION_CLAIM, familyId.toString());
        if (session.branchCode() != null) {
            claims.claim(BRANCH_CLAIM, session.branchCode());
        }
        if (session.actor() != null) {
            claims.claim(ACTOR_CLAIM, session.actor());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY_ID).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
