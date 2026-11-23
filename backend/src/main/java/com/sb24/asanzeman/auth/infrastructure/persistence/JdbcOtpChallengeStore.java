package com.sb24.asanzeman.auth.infrastructure.persistence;

import com.sb24.asanzeman.auth.domain.model.OtpChallenge;
import com.sb24.asanzeman.auth.domain.port.out.OtpChallengeStorePort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * {@code AZS_AUTH.otp_challenge} and {@code otp_lockout} through plain JDBC (6.3, D34). Every method is one statement
 * or a short run of them that stays correct when two requests race: the count goes up in the database, and a code is
 * used by a conditional update.
 */
@Component
public class JdbcOtpChallengeStore implements OtpChallengeStorePort {

    private final JdbcClient jdbc;

    public JdbcOtpChallengeStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(OtpChallenge c) {
        jdbc.sql("""
                INSERT INTO azs_auth.otp_challenge (id, national_code, mobile, otp_id, family_id, sent_at, status)
                VALUES (:id, :nationalCode, :mobile, :otpId, :familyId, :sentAt, 'SENT')
                """)
                .param("id", RawUuid.toBytes(c.id()), Types.VARBINARY)
                .param("nationalCode", c.nationalCode(), Types.VARCHAR)
                .param("mobile", c.mobile(), Types.VARCHAR)
                .param("otpId", c.otpId(), Types.VARCHAR)
                .param("familyId", RawUuid.toBytes(c.familyId()), Types.VARBINARY)
                .param("sentAt", DbTime.toDb(c.sentAt()), Types.TIMESTAMP)
                .update();
    }

    @Override
    public Optional<OtpChallenge> find(UUID id) {
        return jdbc.sql("""
                SELECT national_code, mobile, otp_id, family_id, sent_at, status
                  FROM azs_auth.otp_challenge WHERE id = :id
                """)
                .param("id", RawUuid.toBytes(id), Types.VARBINARY)
                .query((rs, row) -> new OtpChallenge(id, rs.getString("national_code"), rs.getString("mobile"),
                        rs.getString("otp_id"),
                        RawUuid.fromBytes(rs.getBytes("family_id")),
                        DbTime.fromDb(rs.getObject("sent_at", LocalDateTime.class)),
                        "VERIFIED".equals(rs.getString("status"))))
                .optional();
    }

    @Override
    public boolean markVerified(UUID id) {
        return jdbc.sql("UPDATE azs_auth.otp_challenge SET status = 'VERIFIED' WHERE id = :id AND status = 'SENT'")
                .param("id", RawUuid.toBytes(id), Types.VARBINARY).update() == 1;
    }

    @Override
    public boolean isLocked(String nationalCode, Instant now) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM azs_auth.otp_lockout
                 WHERE national_code = :nationalCode AND locked_until > :now
                """)
                .param("nationalCode", nationalCode, Types.VARCHAR)
                .param("now", DbTime.toDb(now), Types.TIMESTAMP)
                .query(Long.class).single() > 0;
    }

    @Override
    public boolean recordWrong(String nationalCode, Instant now, int maxAttempts, Duration lockout) {
        if (countUp(nationalCode) == 0) {
            try {
                jdbc.sql("INSERT INTO azs_auth.otp_lockout (national_code, wrong_attempts) VALUES (:nationalCode, 1)")
                        .param("nationalCode", nationalCode, Types.VARCHAR).update();
            } catch (DuplicateKeyException e) {
                countUp(nationalCode); // another wrong code created the row first
            }
        }
        return jdbc.sql("""
                UPDATE azs_auth.otp_lockout SET locked_until = :until, wrong_attempts = 0
                 WHERE national_code = :nationalCode AND wrong_attempts >= :max
                """)
                .param("until", DbTime.toDb(now.plus(lockout)), Types.TIMESTAMP)
                .param("nationalCode", nationalCode, Types.VARCHAR)
                .param("max", maxAttempts, Types.INTEGER)
                .update() > 0;
    }

    private int countUp(String nationalCode) {
        return jdbc.sql("""
                UPDATE azs_auth.otp_lockout SET wrong_attempts = wrong_attempts + 1
                 WHERE national_code = :nationalCode
                """)
                .param("nationalCode", nationalCode, Types.VARCHAR).update();
    }

    @Override
    public void clearWrong(String nationalCode) {
        jdbc.sql("DELETE FROM azs_auth.otp_lockout WHERE national_code = :nationalCode")
                .param("nationalCode", nationalCode, Types.VARCHAR).update();
    }
}
