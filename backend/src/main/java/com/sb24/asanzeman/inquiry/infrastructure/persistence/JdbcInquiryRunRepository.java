package com.sb24.asanzeman.inquiry.infrastructure.persistence;

import com.sb24.asanzeman.inquiry.domain.model.CivilData;
import com.sb24.asanzeman.inquiry.domain.model.FailureKind;
import com.sb24.asanzeman.inquiry.domain.model.Gender;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryResult;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRun;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict;
import com.sb24.asanzeman.inquiry.domain.model.RunStatus;
import com.sb24.asanzeman.inquiry.domain.port.out.InquiryRunRepositoryPort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code AZS_INQUIRY.inquiry_run} and {@code inquiry_result} through plain JDBC (5.1). The unique {@code
 * active_request_id} is what keeps one running run per request; a run's end and its results are saved together.
 */
@Component
public class JdbcInquiryRunRepository implements InquiryRunRepositoryPort {

    static final String RUN_COLUMNS = "SELECT id, request_id, status, decision, started_at, finished_at"
            + " FROM azs_inquiry.inquiry_run ";
    static final String RESULT_COLUMNS = "SELECT inquiry_type, result, failure_kind, message_key, gender, birth_date";

    private final JdbcClient jdbc;

    public JdbcInquiryRunRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void markStale(UUID requestId, Instant before) {
        jdbc.sql("""
                UPDATE azs_inquiry.inquiry_run SET status = 'STALE', active_request_id = NULL
                 WHERE request_id = :request AND status = 'RUNNING' AND started_at < :before
                """)
                .param("request", RawUuid.toBytes(requestId), Types.VARBINARY)
                .param("before", DbTime.toDb(before), Types.TIMESTAMP).update();
    }

    @Override
    public boolean insertRunning(InquiryRun run) {
        try {
            jdbc.sql("""
                    INSERT INTO azs_inquiry.inquiry_run (id, request_id, status, active_request_id, started_at)
                    VALUES (:id, :request, 'RUNNING', :request, :startedAt)
                    """)
                    .param("id", RawUuid.toBytes(run.id()), Types.VARBINARY)
                    .param("request", RawUuid.toBytes(run.requestId()), Types.VARBINARY)
                    .param("startedAt", DbTime.toDb(run.startedAt()), Types.TIMESTAMP).update();
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public Optional<InquiryRun> findRunning(UUID requestId) {
        return jdbc.sql(RUN_COLUMNS + "WHERE request_id = :request AND status = 'RUNNING'")
                .param("request", RawUuid.toBytes(requestId), Types.VARBINARY)
                .query((rs, row) -> run(rs)).optional();
    }

    @Override
    public Optional<InquiryRun> findLatest(UUID requestId) {
        return jdbc.sql(RUN_COLUMNS + "WHERE request_id = :request ORDER BY started_at DESC FETCH FIRST 1 ROWS ONLY")
                .param("request", RawUuid.toBytes(requestId), Types.VARBINARY)
                .query((rs, row) -> run(rs)).optional();
    }

    @Override
    public Optional<InquiryRun> find(UUID requestId, UUID runId) {
        return jdbc.sql(RUN_COLUMNS + "WHERE request_id = :request AND id = :id")
                .param("request", RawUuid.toBytes(requestId), Types.VARBINARY)
                .param("id", RawUuid.toBytes(runId), Types.VARBINARY)
                .query((rs, row) -> run(rs)).optional();
    }

    @Override
    @Transactional
    public boolean finish(UUID runId, InquiryVerdict.Decision decision, List<InquiryOutcome> outcomes, Instant at) {
        int ended = jdbc.sql("""
                UPDATE azs_inquiry.inquiry_run
                   SET status = 'FINISHED', decision = :decision, finished_at = :at, active_request_id = NULL
                 WHERE id = :id AND status = 'RUNNING'
                """)
                .param("decision", decision.name(), Types.VARCHAR)
                .param("at", DbTime.toDb(at), Types.TIMESTAMP)
                .param("id", RawUuid.toBytes(runId), Types.VARBINARY).update();
        if (ended == 0) {
            return false;
        }
        for (InquiryOutcome o : outcomes) {
            jdbc.sql("""
                    INSERT INTO azs_inquiry.inquiry_result
                        (run_id, inquiry_type, result, failure_kind, message_key, gender, birth_date, finished_at)
                    VALUES (:run, :type, :result, :failure, :message, :gender, :birthDate, :at)
                    """)
                    .param("run", RawUuid.toBytes(runId), Types.VARBINARY)
                    .param("type", o.type().name(), Types.VARCHAR)
                    .param("result", o.result().name(), Types.VARCHAR)
                    .param("failure", o.failure() == null ? null : o.failure().name(), Types.VARCHAR)
                    .param("message", o.messageKey(), Types.VARCHAR)
                    .param("gender", o.civil() == null ? null : o.civil().gender().name(), Types.VARCHAR)
                    .param("birthDate", o.civil() == null ? null : o.civil().birthDate(), Types.DATE)
                    .param("at", DbTime.toDb(at), Types.TIMESTAMP).update();
        }
        return true;
    }

    @Override
    public List<InquiryOutcome> results(UUID runId) {
        return jdbc.sql(RESULT_COLUMNS + " FROM azs_inquiry.inquiry_result WHERE run_id = :run ORDER BY inquiry_type")
                .param("run", RawUuid.toBytes(runId), Types.VARBINARY)
                .query((rs, row) -> outcome(rs)).list();
    }

    @Override
    public Map<InquiryType, InquiryOutcome> passedSince(UUID requestId, Instant since) {
        List<InquiryOutcome> passed = jdbc.sql("""
                SELECT r.inquiry_type, r.result, r.failure_kind, r.message_key, r.gender, r.birth_date
                  FROM azs_inquiry.inquiry_result r JOIN azs_inquiry.inquiry_run u ON u.id = r.run_id
                 WHERE u.request_id = :request AND r.result = 'PASSED' AND r.finished_at >= :since
                 ORDER BY r.finished_at
                """)
                .param("request", RawUuid.toBytes(requestId), Types.VARBINARY)
                .param("since", DbTime.toDb(since), Types.TIMESTAMP)
                .query((rs, row) -> outcome(rs)).list();
        Map<InquiryType, InquiryOutcome> newest = new EnumMap<>(InquiryType.class);
        passed.forEach(o -> newest.put(o.type(), o)); // oldest first, so the newest answer of each inquiry wins
        return newest;
    }

    private static InquiryRun run(ResultSet rs) throws SQLException {
        String decision = rs.getString("decision");
        LocalDateTime finished = rs.getObject("finished_at", LocalDateTime.class);
        return new InquiryRun(RawUuid.fromBytes(rs.getBytes("id")), RawUuid.fromBytes(rs.getBytes("request_id")),
                RunStatus.valueOf(rs.getString("status")),
                decision == null ? null : InquiryVerdict.Decision.valueOf(decision),
                DbTime.fromDb(rs.getObject("started_at", LocalDateTime.class)),
                finished == null ? null : DbTime.fromDb(finished));
    }

    private static InquiryOutcome outcome(ResultSet rs) throws SQLException {
        String failure = rs.getString("failure_kind");
        String gender = rs.getString("gender");
        LocalDate birthDate = rs.getObject("birth_date", LocalDate.class);
        CivilData civil = gender == null || birthDate == null ? null : new CivilData(Gender.valueOf(gender), birthDate);
        return new InquiryOutcome(InquiryType.valueOf(rs.getString("inquiry_type")),
                InquiryResult.valueOf(rs.getString("result")), rs.getString("message_key"),
                failure == null ? null : FailureKind.valueOf(failure), civil);
    }
}
