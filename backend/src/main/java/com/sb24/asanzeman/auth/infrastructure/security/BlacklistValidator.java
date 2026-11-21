package com.sb24.asanzeman.auth.infrastructure.security;

import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Rejects an access token whose {@code jti} was revoked at logout. A token without a {@code jti} is rejected too. */
public class BlacklistValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "The token has been revoked", null);

    private final JwtBlacklistPort blacklist;

    public BlacklistValidator(JwtBlacklistPort blacklist) {
        this.blacklist = blacklist;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String jti = jwt.getId();
        if (jti == null || blacklist.isRevoked(jti)) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
