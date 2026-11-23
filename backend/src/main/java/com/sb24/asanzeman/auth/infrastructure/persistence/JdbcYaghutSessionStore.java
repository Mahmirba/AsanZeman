package com.sb24.asanzeman.auth.infrastructure.persistence;

import com.sb24.asanzeman.auth.domain.model.YaghutUserSession;
import com.sb24.asanzeman.auth.domain.port.out.YaghutSessionStorePort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
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
                .param("expiresAt", DbTime.toDb(expiresAt), Types.TIMESTAMP)
                .update();
    }

    @Override
    public Optional<YaghutUserSession> find(UUID familyId) {
        return jdbc.sql("SELECT jsession_id, expires_at FROM azs_auth.yaghut_session WHERE family_id = :familyId")
                .param("familyId", RawUuid.toBytes(familyId), Types.VARBINARY)
                .query((rs, row) -> new YaghutUserSession(rs.getString("jsession_id"),
                        DbTime.fromDb(rs.getObject("expires_at", java.time.LocalDateTime.class))))
                .optional();
    }

    @Override
    public int deleteExpiredBefore(Instant before) {
        return jdbc.sql("DELETE FROM azs_auth.yaghut_session WHERE COALESCE(expires_at, created_at) < :before")
                .param("before", DbTime.toDb(before), Types.TIMESTAMP).update();
    }

    @Override
    public void delete(UUID familyId) {
        jdbc.sql("DELETE FROM azs_auth.yaghut_session WHERE family_id = :familyId")
                .param("familyId", RawUuid.toBytes(familyId), Types.VARBINARY).update();
    }
}
