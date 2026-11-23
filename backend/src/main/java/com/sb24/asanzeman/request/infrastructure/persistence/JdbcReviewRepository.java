package com.sb24.asanzeman.request.infrastructure.persistence;

import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.Review;
import com.sb24.asanzeman.request.domain.model.ReviewLock;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.ReviewRepositoryPort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ownership lock and the branch's records on {@code AZS_BUSINESS.request} through plain JDBC (4.1). The row is read
 * with {@code FOR UPDATE NOWAIT} by the request repository, so two users taking the same request cannot both win; the
 * lock columns then say who owns it between calls.
 */
@Component
public class JdbcReviewRepository implements ReviewRepositoryPort {

    private final JdbcClient jdbc;
    private final RequestRepositoryPort requests;

    public JdbcReviewRepository(JdbcClient jdbc, RequestRepositoryPort requests) {
        this.jdbc = jdbc;
        this.requests = requests;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Review> findForUpdate(UUID requestId) {
        Optional<Request> request = requests.findByIdForUpdate(requestId);
        return request.map(r -> jdbc.sql("""
                SELECT q.locked_by, q.locked_by_name, q.locked_at, q.last_activity_at, b.parent_branch
                  FROM azs_business.request q JOIN azs_business.branch b ON b.branch_code = q.branch_code
                 WHERE q.id = :id
                """).param("id", RawUuid.toBytes(requestId), Types.VARBINARY)
                .query((rs, row) -> new Review(r, lock(rs), rs.getString("parent_branch"))).single());
    }

    private static ReviewLock lock(java.sql.ResultSet rs) throws java.sql.SQLException {
        byte[] holder = rs.getBytes("locked_by");
        return holder == null ? null : new ReviewLock(RawUuid.fromBytes(holder), rs.getString("locked_by_name"),
                DbTime.fromDb(rs.getObject("locked_at", LocalDateTime.class)),
                DbTime.fromDb(rs.getObject("last_activity_at", LocalDateTime.class)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void save(UUID requestId, RequestStatus status, ReviewLock lock, Instant at) {
        jdbc.sql("""
                UPDATE azs_business.request
                   SET status = :status, locked_by = :holder, locked_by_name = :holderName, locked_at = :lockedAt,
                       last_activity_at = :activity, version = version + 1, updated_at = :at
                 WHERE id = :id
                """)
                .param("status", status.name(), Types.VARCHAR)
                .param("holder", lock == null ? null : RawUuid.toBytes(lock.holderId()), Types.VARBINARY)
                .param("holderName", lock == null ? null : lock.holderName(), Types.VARCHAR)
                .param("lockedAt", lock == null ? null : DbTime.toDb(lock.lockedAt()), Types.TIMESTAMP)
                .param("activity", lock == null ? null : DbTime.toDb(lock.lastActivityAt()), Types.TIMESTAMP)
                .param("at", DbTime.toDb(at), Types.TIMESTAMP)
                .param("id", RawUuid.toBytes(requestId), Types.VARBINARY).update();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRejection(UUID requestId, UUID reasonId, String comment) {
        jdbc.sql("UPDATE azs_business.request SET rejection_reason_id = :reason, rejection_comment = :comment "
                + "WHERE id = :id")
                .param("reason", RawUuid.toBytes(reasonId), Types.VARBINARY).param("comment", comment, Types.VARCHAR)
                .param("id", RawUuid.toBytes(requestId), Types.VARBINARY).update();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordIssue(UUID requestId, String guaranteeNumber, Instant at) {
        jdbc.sql("UPDATE azs_business.request SET guarantee_number = :number, issued_at = :at WHERE id = :id")
                .param("number", guaranteeNumber, Types.VARCHAR).param("at", DbTime.toDb(at), Types.TIMESTAMP)
                .param("id", RawUuid.toBytes(requestId), Types.VARBINARY).update();
    }

    @Override
    public List<UUID> idleReviews(Instant before) {
        return jdbc.sql("SELECT id FROM azs_business.request WHERE status = 'UNDER_REVIEW' "
                + "AND last_activity_at <= :before ORDER BY last_activity_at")
                .param("before", DbTime.toDb(before), Types.TIMESTAMP)
                .query((rs, row) -> RawUuid.fromBytes(rs.getBytes("id"))).list();
    }

    @Override
    public List<com.sb24.asanzeman.request.domain.model.RejectionReason> issuanceReasons() {
        return jdbc.sql("SELECT id, text FROM azs_business.rejection_reason "
                + "WHERE kind = 'ISSUANCE' AND active = 1 ORDER BY sort_order, text")
                .query((rs, row) -> new com.sb24.asanzeman.request.domain.model.RejectionReason(
                        RawUuid.fromBytes(rs.getBytes("id")), rs.getString("text"))).list();
    }

    @Override
    public boolean issuanceReasonExists(UUID reasonId) {
        return jdbc.sql("SELECT COUNT(*) FROM azs_business.rejection_reason "
                + "WHERE id = :id AND kind = 'ISSUANCE' AND active = 1")
                .param("id", RawUuid.toBytes(reasonId), Types.VARBINARY).query(Integer.class).single() > 0;
    }
}
