package com.sb24.asanzeman.auth.infrastructure.persistence;

import com.sb24.asanzeman.auth.domain.port.out.YaghutSessionStorePort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@code AZS_AUTH.yaghut_session}: the user's own Yaghut {@code JSESSIONID}, one row per Asan Zeman session. */
@Component
public class JdbcYaghutSessionStore implements YaghutSessionStorePort {

    static final String INSERT = """
            INSERT INTO azs_auth.yaghut_session (family_id, jsession_id, expires_at)
            VALUES (:familyId, :jsessionId, :expiresAt)
            """;

    private final JdbcClient jdbc;

    public JdbcYaghutSessionStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(UUID familyId, String jsessionId, Instant expiresAt) {
        jdbc.sql(INSERT)
                .param("familyId", RawUuid.toBytes(familyId), Types.VARBINARY)
                .param("jsessionId", jsessionId, Types.VARCHAR)
                .param("expiresAt", expiresAt == null ? null : DbTime.toDb(expiresAt), Types.TIMESTAMP)
                .update();
    }
}
