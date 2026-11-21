package com.sb24.asanzeman.notification.infrastructure.persistence;

import com.sb24.asanzeman.notification.domain.model.OutboxSms;
import com.sb24.asanzeman.notification.domain.port.out.SmsOutboxPort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code AZS_OUTBOX.outbox_message} through plain JDBC (6.4). A message is claimed with one conditional
 * {@code UPDATE}: it moves the next attempt forward and counts the attempt, but only if the message is still pending
 * and still due. If two workers pick the same message, only one update changes a row, so only one sends it. No
 * {@code SKIP LOCKED} is needed, and no row limit combined with {@code FOR UPDATE}, which Oracle does not allow.
 */
@Component
public class JdbcSmsOutbox implements SmsOutboxPort {

    static final String INSERT = """
            INSERT INTO azs_outbox.outbox_message (id, mobile, template_code, message_text, request_id, status,
                                                   attempts, next_attempt_at)
            VALUES (:id, :mobile, :template, :text, :request, 'PENDING', 0, :now)
            """;
    static final String SELECT_DUE = """
            SELECT id, mobile, template_code, message_text, request_id, attempts
              FROM azs_outbox.outbox_message
             WHERE status = 'PENDING' AND next_attempt_at <= :now
             ORDER BY next_attempt_at, created_at
             FETCH FIRST %d ROWS ONLY
            """;
    static final String CLAIM = """
            UPDATE azs_outbox.outbox_message
               SET attempts = attempts + 1, next_attempt_at = :leaseEnd, updated_at = :now
             WHERE id = :id AND status = 'PENDING' AND next_attempt_at <= :now
            """;
    static final String SENT = """
            UPDATE azs_outbox.outbox_message
               SET status = 'SENT', sms_id = :smsId, sent_at = :at, last_error = NULL, updated_at = :at
             WHERE id = :id
            """;
    static final String RETRY = """
            UPDATE azs_outbox.outbox_message SET next_attempt_at = :next, last_error = :error WHERE id = :id
            """;
    static final String DEAD = """
            UPDATE azs_outbox.outbox_message
               SET status = 'DEAD', last_error = :error, updated_at = :at WHERE id = :id
            """;

    private final JdbcClient jdbc;

    public JdbcSmsOutbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(OutboxSms sms, Instant now) {
        jdbc.sql(INSERT)
                .param("id", RawUuid.toBytes(sms.id()), Types.VARBINARY)
                .param("mobile", sms.mobile(), Types.VARCHAR)
                .param("template", sms.templateCode(), Types.VARCHAR)
                .param("text", sms.text(), Types.VARCHAR)
                .param("request", sms.requestId() == null ? null : RawUuid.toBytes(sms.requestId()), Types.VARBINARY)
                .param("now", DbTime.toDb(now), Types.TIMESTAMP)
                .update();
    }

    @Override
    @Transactional
    public List<OutboxSms> claimDue(Instant now, int limit, Duration lease) {
        var at = DbTime.toDb(now);
        var leaseEnd = DbTime.toDb(now.plus(lease));
        List<OutboxSms> candidates = jdbc.sql(SELECT_DUE.formatted(limit)).param("now", at, Types.TIMESTAMP)
                .query((rs, row) -> new OutboxSms(RawUuid.fromBytes(rs.getBytes("id")), rs.getString("mobile"),
                        rs.getString("template_code"), rs.getString("message_text"),
                        rs.getBytes("request_id") == null ? null : RawUuid.fromBytes(rs.getBytes("request_id")),
                        rs.getInt("attempts")))
                .list();
        List<OutboxSms> claimed = new ArrayList<>();
        for (OutboxSms sms : candidates) {
            int won = jdbc.sql(CLAIM).param("id", RawUuid.toBytes(sms.id()), Types.VARBINARY)
                    .param("now", at, Types.TIMESTAMP).param("leaseEnd", leaseEnd, Types.TIMESTAMP).update();
            if (won == 1) {
                claimed.add(new OutboxSms(sms.id(), sms.mobile(), sms.templateCode(), sms.text(), sms.requestId(),
                        sms.attempts() + 1));
            }
        }
        return claimed;
    }

    @Override
    public void markSent(UUID id, String smsId, Instant at) {
        jdbc.sql(SENT).param("id", RawUuid.toBytes(id), Types.VARBINARY).param("smsId", smsId, Types.VARCHAR)
                .param("at", DbTime.toDb(at), Types.TIMESTAMP).update();
    }

    @Override
    public void markRetry(UUID id, Instant nextAttempt, String error) {
        jdbc.sql(RETRY).param("id", RawUuid.toBytes(id), Types.VARBINARY)
                .param("next", DbTime.toDb(nextAttempt), Types.TIMESTAMP).param("error", error, Types.VARCHAR)
                .update();
    }

    @Override
    public void markDead(UUID id, String error, Instant at) {
        jdbc.sql(DEAD).param("id", RawUuid.toBytes(id), Types.VARBINARY).param("error", error, Types.VARCHAR)
                .param("at", DbTime.toDb(at), Types.TIMESTAMP).update();
    }
}
