package com.sb24.asanzeman.auth.infrastructure.persistence;

import com.sb24.asanzeman.auth.domain.port.out.JwtBlacklistPort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import java.sql.Types;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code AZS_AUTH.jwt_blacklist} through plain JDBC. Rows are only needed until the token would have expired. */
@Component
public class JdbcJwtBlacklist implements JwtBlacklistPort {

    static final String INSERT = """
            INSERT INTO azs_auth.jwt_blacklist (jti, expires_at, reason) VALUES (:jti, :expiresAt, :reason)
            """;
    static final String COUNT = "SELECT COUNT(*) FROM azs_auth.jwt_blacklist WHERE jti = :jti";

    private final JdbcClient jdbc;

    public JdbcJwtBlacklist(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void revoke(String jti, Instant expiresAt, String reason) {
        try {
            jdbc.sql(INSERT).param("jti", jti, Types.VARCHAR)
                    .param("expiresAt", DbTime.toDb(expiresAt), Types.TIMESTAMP)
                    .param("reason", reason, Types.VARCHAR).update();
        } catch (DuplicateKeyException e) {
            // already revoked: logging out twice is harmless
        }
    }

    @Override
    public int deleteExpired(Instant now) {
        return jdbc.sql("DELETE FROM azs_auth.jwt_blacklist WHERE expires_at < :now")
                .param("now", DbTime.toDb(now), Types.TIMESTAMP).update();
    }

    @Override
    public boolean isRevoked(String jti) {
        return jdbc.sql(COUNT).param("jti", jti, Types.VARCHAR).query(Integer.class).single() > 0;
    }
}
