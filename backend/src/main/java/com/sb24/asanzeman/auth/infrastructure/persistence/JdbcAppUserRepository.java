package com.sb24.asanzeman.auth.infrastructure.persistence;

import com.sb24.asanzeman.auth.domain.model.AppUser;
import com.sb24.asanzeman.auth.domain.model.Personnel;
import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.port.out.AppUserRepositoryPort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@code AZS_AUTH.app_user} through plain JDBC. The first login inserts the user; every later login updates the
 * details, branch and role from personnelInfo (D33). Two first logins of the same user at once are safe: the second
 * insert hits the unique account name and falls back to the update.
 */
@Component
public class JdbcAppUserRepository implements AppUserRepositoryPort {

    static final String SELECT_ID = "SELECT id FROM azs_auth.app_user WHERE account_name = :name";
    static final String INSERT = """
            INSERT INTO azs_auth.app_user (id, account_name, prsn_code, personel_user_name, branch_code, branch_name,
                                           job_position, job, role, last_login_at)
            VALUES (:id, :name, :prsnCode, :personelUserName, :branchCode, :branchName, :position, :job, :role, :now)
            """;
    static final String UPDATE = """
            UPDATE azs_auth.app_user
               SET prsn_code = :prsnCode, personel_user_name = :personelUserName, branch_code = :branchCode,
                   branch_name = :branchName, job_position = :position, job = :job, role = :role,
                   last_login_at = :now, updated_at = :now
             WHERE id = :id
            """;

    private final JdbcClient jdbc;

    public JdbcAppUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AppUser upsert(Personnel personnel, Role role, Instant now) {
        String name = personnel.accountName().value();
        Optional<UUID> existing = findId(name);
        UUID id;
        if (existing.isPresent()) {
            id = existing.get();
        } else {
            id = UUID.randomUUID();
            try {
                bind(jdbc.sql(INSERT), id, personnel, role, now).update();
                return new AppUser(id, personnel.accountName(), personnel.branchCode(), role);
            } catch (DuplicateKeyException e) {
                // another login of the same user inserted first: update that row instead
                id = findId(name).orElseThrow(() -> e);
            }
        }
        bind(jdbc.sql(UPDATE), id, personnel, role, now).update();
        return new AppUser(id, personnel.accountName(), personnel.branchCode(), role);
    }

    Optional<UUID> findId(String accountName) {
        return jdbc.sql(SELECT_ID).param("name", accountName, Types.VARCHAR)
                .query((rs, row) -> RawUuid.fromBytes(rs.getBytes("id"))).optional();
    }

    private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, UUID id, Personnel p, Role role,
            Instant now) {
        return spec.param("id", RawUuid.toBytes(id), Types.VARBINARY)
                .param("name", p.accountName().value(), Types.VARCHAR)
                .param("prsnCode", p.prsnCode(), Types.VARCHAR)
                .param("personelUserName", p.personelUserName(), Types.VARCHAR)
                .param("branchCode", p.branchCode(), Types.VARCHAR)
                .param("branchName", p.branchName(), Types.VARCHAR)
                .param("position", p.position(), Types.VARCHAR)
                .param("job", p.job(), Types.VARCHAR)
                .param("role", role.name(), Types.VARCHAR)
                .param("now", DbTime.toDb(now), Types.TIMESTAMP);
    }
}
