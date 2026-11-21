package com.sb24.asanzeman.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.notification.domain.model.OutboxSms;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import com.sb24.asanzeman.support.H2Auth;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real SQL on in-memory H2; the MANDATORY transaction is the caller's, so the tests call the SQL part directly. */
class JdbcSmsOutboxTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final Duration LEASE = Duration.ofMinutes(2);

    private final JdbcClient jdbc = H2Auth.newDatabase();
    private final JdbcSmsOutbox outbox = new JdbcSmsOutbox(jdbc);

    private OutboxSms queue(String text, Instant at) {
        OutboxSms sms = new OutboxSms(UUID.randomUUID(), "09121234567", "REQUEST_REGISTERED", text, UUID.randomUUID(),
                0);
        outbox.insert(sms, at);
        return sms;
    }

    private String column(OutboxSms sms, String column) {
        return jdbc.sql("SELECT " + column + " FROM azs_outbox.outbox_message WHERE id = :id")
                .param("id", RawUuid.toBytes(sms.id()), Types.VARBINARY).query(String.class).single();
    }

    @Test
    void aQueuedMessageIsPendingAndDueAtOnce() {
        OutboxSms sms = queue("hello", NOW);

        List<OutboxSms> due = outbox.claimDue(NOW, 10, LEASE);

        assertThat(due).hasSize(1);
        assertThat(due.get(0)).isEqualTo(new OutboxSms(sms.id(), "09121234567", "REQUEST_REGISTERED", "hello",
                sms.requestId(), 1));
        assertThat(column(sms, "status")).isEqualTo("PENDING");
    }

    @Test
    void aMessageWithoutARequestRoundTrips() {
        OutboxSms sms = new OutboxSms(UUID.randomUUID(), "09121234567", "REQUEST_REGISTERED", "hello", null, 0);
        outbox.insert(sms, NOW);

        assertThat(outbox.claimDue(NOW, 10, LEASE)).extracting(OutboxSms::requestId).containsExactly((UUID) null);
    }

    @Test
    void aClaimedMessageIsHiddenForTheLeaseSoNoSecondWorkerSendsItToo() {
        queue("hello", NOW);

        assertThat(outbox.claimDue(NOW, 10, LEASE)).hasSize(1);
        assertThat(outbox.claimDue(NOW, 10, LEASE)).isEmpty();
        assertThat(outbox.claimDue(NOW.plus(LEASE).minusSeconds(1), 10, LEASE)).isEmpty();
    }

    @Test
    void aMessageWhoseWorkerDiedIsTriedAgainAfterTheLeaseAndCountsTheAttempt() {
        queue("hello", NOW);
        outbox.claimDue(NOW, 10, LEASE);

        List<OutboxSms> again = outbox.claimDue(NOW.plus(LEASE), 10, LEASE);

        assertThat(again).extracting(OutboxSms::attempts).containsExactly(2);
    }

    @Test
    void aMessageNotYetDueIsNotClaimed() {
        queue("later", NOW.plusSeconds(60));

        assertThat(outbox.claimDue(NOW, 10, LEASE)).isEmpty();
        assertThat(outbox.claimDue(NOW.plusSeconds(60), 10, LEASE)).hasSize(1);
    }

    @Test
    void theBatchIsLimitedAndTheOldestComeFirst() {
        OutboxSms oldest = queue("one", NOW.minusSeconds(30));
        queue("two", NOW.minusSeconds(20));
        queue("three", NOW.minusSeconds(10));

        List<OutboxSms> due = outbox.claimDue(NOW, 2, LEASE);

        assertThat(due).hasSize(2);
        assertThat(due.get(0).id()).isEqualTo(oldest.id());
        assertThat(outbox.claimDue(NOW, 10, LEASE)).hasSize(1);
    }

    @Test
    void twoWorkersCannotBothClaimTheSameMessage() {
        OutboxSms sms = queue("hello", NOW);
        // the second worker read the row before the first one claimed it
        var stale = jdbc.sql(JdbcSmsOutbox.CLAIM).param("id", RawUuid.toBytes(sms.id()), Types.VARBINARY)
                .param("now", com.sb24.asanzeman.shared.persistence.DbTime.toDb(NOW), Types.TIMESTAMP)
                .param("leaseEnd", com.sb24.asanzeman.shared.persistence.DbTime.toDb(NOW.plus(LEASE)),
                        Types.TIMESTAMP);

        assertThat(stale.update()).isEqualTo(1);
        assertThat(jdbc.sql(JdbcSmsOutbox.CLAIM).param("id", RawUuid.toBytes(sms.id()), Types.VARBINARY)
                .param("now", com.sb24.asanzeman.shared.persistence.DbTime.toDb(NOW), Types.TIMESTAMP)
                .param("leaseEnd", com.sb24.asanzeman.shared.persistence.DbTime.toDb(NOW.plus(LEASE)),
                        Types.TIMESTAMP).update()).isZero();
    }

    @Test
    void sentMessagesKeepTheSmsIdAndLeaveTheQueue() {
        OutboxSms sms = queue("hello", NOW);
        outbox.claimDue(NOW, 10, LEASE);

        outbox.markSent(sms.id(), "SMS-9", NOW);

        assertThat(column(sms, "status")).isEqualTo("SENT");
        assertThat(column(sms, "sms_id")).isEqualTo("SMS-9");
        assertThat(outbox.claimDue(NOW.plus(Duration.ofDays(1)), 10, LEASE)).isEmpty();
    }

    @Test
    void aRetryKeepsTheMessagePendingForALaterTimeAndRecordsTheError() {
        OutboxSms sms = queue("hello", NOW);
        outbox.claimDue(NOW, 10, LEASE);

        outbox.markRetry(sms.id(), NOW.plusSeconds(30), "timeout");

        assertThat(column(sms, "status")).isEqualTo("PENDING");
        assertThat(column(sms, "last_error")).isEqualTo("timeout");
        assertThat(outbox.claimDue(NOW.plusSeconds(29), 10, LEASE)).isEmpty();
        assertThat(outbox.claimDue(NOW.plusSeconds(30), 10, LEASE)).hasSize(1);
    }

    @Test
    void aDeadMessageIsNeverClaimedAgain() {
        OutboxSms sms = queue("hello", NOW);
        outbox.claimDue(NOW, 10, LEASE);

        outbox.markDead(sms.id(), "bad number", NOW);

        assertThat(column(sms, "status")).isEqualTo("DEAD");
        assertThat(column(sms, "last_error")).isEqualTo("bad number");
        assertThat(outbox.claimDue(NOW.plus(Duration.ofDays(1)), 10, LEASE)).isEmpty();
    }
}
