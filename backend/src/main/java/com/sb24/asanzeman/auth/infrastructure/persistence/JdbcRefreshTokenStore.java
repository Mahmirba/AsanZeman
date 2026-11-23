package com.sb24.asanzeman.auth.infrastructure.persistence;

import com.sb24.asanzeman.auth.domain.model.RefreshTokenRecord;
import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.auth.domain.port.out.RefreshTokenStorePort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@code AZS_AUTH.refresh_tokens} through plain JDBC. {@link #markUsed} is a single conditional {@code UPDATE}, which
 * is what makes rotation safe when the same token arrives twice at once: the database lets only one of them win.
 */
@Component
public class JdbcRefreshTokenStore implements RefreshTokenStorePort {

    static final String INSERT = """
            INSERT INTO azs_auth.refresh_tokens (id, family_id, subject_id, subject_name, role, branch_code,
                                                 token_hash, expires_at, used_at, revoked_at)
            VALUES (:id, :familyId, :subjectId, :subjectName, :role, :branchCode, :tokenHash, :expiresAt, :usedAt,
                    :revokedAt)
            """;
    static final String SELECT_BY_HASH = """
            SELECT id, family_id, subject_id, subject_name, role, branch_code, token_hash, expires_at, used_at,
                   revoked_at
              FROM azs_auth.refresh_tokens WHERE token_hash = :hash
            """;
    static final String MARK_USED = """
            UPDATE azs_auth.refresh_tokens SET used_at = :at
             WHERE id = :id AND used_at IS NULL AND revoked_at IS NULL
            """;
    static final String REVOKE_FAMILY = """
            UPDATE azs_auth.refresh_tokens SET revoked_at = :at WHERE family_id = :familyId AND revoked_at IS NULL
            """;

    private final JdbcClient jdbc;

    public JdbcRefreshTokenStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(RefreshTokenRecord token) {
        Session s = token.session();
        jdbc.sql(INSERT)
                .param("id", RawUuid.toBytes(token.id()), Types.VARBINARY)
                .param("familyId", RawUuid.toBytes(token.familyId()), Types.VARBINARY)
                .param("subjectId", RawUuid.toBytes(s.subjectId()), Types.VARBINARY)
                .param("subjectName", s.subjectName(), Types.VARCHAR)
                .param("role", s.role().name(), Types.VARCHAR)
                .param("branchCode", s.branchCode(), Types.VARCHAR)
                .param("tokenHash", token.tokenHash(), Types.VARCHAR)
                .param("expiresAt", DbTime.toDb(token.expiresAt()), Types.TIMESTAMP)
                .param("usedAt", DbTime.toDb(token.usedAt()), Types.TIMESTAMP)
                .param("revokedAt", DbTime.toDb(token.revokedAt()), Types.TIMESTAMP)
                .update();
    }

    @Override
    public int deleteExpiredBefore(java.time.Instant before) {
        return jdbc.sql("DELETE FROM azs_auth.refresh_tokens WHERE expires_at < :before")
                .param("before", DbTime.toDb(before), java.sql.Types.TIMESTAMP).update();
    }

    @Override
    public Optional<RefreshTokenRecord> findByHash(String tokenHash) {
        return jdbc.sql(SELECT_BY_HASH).param("hash", tokenHash, Types.VARCHAR)
                .query((rs, row) -> map(rs)).optional();
    }

    @Override
    public boolean markUsed(UUID id, Instant at) {
        return jdbc.sql(MARK_USED).param("id", RawUuid.toBytes(id), Types.VARBINARY)
                .param("at", DbTime.toDb(at), Types.TIMESTAMP).update() == 1;
    }

    @Override
    public void revokeFamily(UUID familyId, Instant at) {
        jdbc.sql(REVOKE_FAMILY).param("familyId", RawUuid.toBytes(familyId), Types.VARBINARY)
                .param("at", DbTime.toDb(at), Types.TIMESTAMP).update();
    }

    private static RefreshTokenRecord map(ResultSet rs) throws SQLException {
        Session session = new Session(RawUuid.fromBytes(rs.getBytes("subject_id")), rs.getString("subject_name"),
                Role.valueOf(rs.getString("role")), rs.getString("branch_code"));
        return new RefreshTokenRecord(RawUuid.fromBytes(rs.getBytes("id")),
                RawUuid.fromBytes(rs.getBytes("family_id")), session, rs.getString("token_hash"),
                DbTime.fromDb(rs.getObject("expires_at", LocalDateTime.class)),
                DbTime.fromDb(rs.getObject("used_at", LocalDateTime.class)),
                DbTime.fromDb(rs.getObject("revoked_at", LocalDateTime.class)));
    }
}
