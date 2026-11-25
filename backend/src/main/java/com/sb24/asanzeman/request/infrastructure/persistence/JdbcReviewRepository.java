package com.sb24.asanzeman.request.infrastructure.persistence;

import com.sb24.asanzeman.request.domain.model.GuaranteeNumberTakenException;
import com.sb24.asanzeman.request.domain.model.RejectionReason;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.Review;
import com.sb24.asanzeman.request.domain.model.ReviewLock;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.ReviewRepositoryPort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import com.sb24.asanzeman.shared.persistence.RawUuid;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ownership lock, the revocation link and the branch's records on {@code AZS_BUSINESS.request} through plain JDBC
 * (4.1, 4.2). The row is read with {@code FOR UPDATE NOWAIT} by the request repository, so two users taking the same
 * request cannot both win; the lock columns then say who owns it between calls.
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
                SELECT q.locked_by, q.locked_by_name, q.locked_at, q.last_activity_at, q.parent_request_id,
                       b.parent_branch,
                       (SELECT COUNT(*) FROM azs_business.request c
                         WHERE c.parent_request_id = q.id AND c.request_type = 'REVOCATION'
                           AND c.status IN ('PENDING_BRANCH_REVIEW', 'UNDER_REVIEW', 'UNDER_ACTION')) AS open_revocations
                  FROM azs_business.request q JOIN azs_business.branch b ON b.branch_code = q.branch_code
                 WHERE q.id = :id
                """).param("id", RawUuid.toBytes(requestId), Types.VARBINARY)
                .query((rs, row) -> new Review(r, lock(rs), rs.getString("parent_branch"),
                        rs.getBytes("parent_request_id") == null ? null
                                : RawUuid.fromBytes(rs.getBytes("parent_request_id")),
                        rs.getInt("open_revocations") > 0)).single());
    }

    private static ReviewLock lock(ResultSet rs) throws SQLException {
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
    public void insertRevocation(Request revocation, UUID parentId, ReviewLock lock) {
        requests.insert(revocation);
        jdbc.sql("UPDATE azs_business.request SET parent_request_id = :parent WHERE id = :id")
                .param("parent", RawUuid.toBytes(parentId), Types.VARBINARY)
                .param("id", RawUuid.toBytes(revocation.id()), Types.VARBINARY).update();
        if (lock != null) {
            save(revocation.id(), revocation.status(), lock, lock.lockedAt());
        }
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
        try {
            jdbc.sql("UPDATE azs_business.request SET guarantee_number = :number, issued_at = :at WHERE id = :id")
                .param("number", guaranteeNumber, Types.VARCHAR).param("at", DbTime.toDb(at), Types.TIMESTAMP)
                .param("id", RawUuid.toBytes(requestId), Types.VARBINARY).update();
        } catch (DuplicateKeyException e) {
            throw new GuaranteeNumberTakenException(); // the unique index: another request has this number
        }
    }

    @Override
    public List<UUID> idleReviews(Instant before) {
        return jdbc.sql("SELECT id FROM azs_business.request WHERE status = 'UNDER_REVIEW' "
                + "AND last_activity_at <= :before ORDER BY last_activity_at")
                .param("before", DbTime.toDb(before), Types.TIMESTAMP)
                .query((rs, row) -> RawUuid.fromBytes(rs.getBytes("id"))).list();
    }

    @Override
    public List<RejectionReason> reasons(String kind) {
        return jdbc.sql("SELECT id, text FROM azs_business.rejection_reason "
                + "WHERE kind = :kind AND active = 1 ORDER BY sort_order, text")
                .param("kind", kind, Types.VARCHAR)
                .query((rs, row) -> new RejectionReason(RawUuid.fromBytes(rs.getBytes("id")), rs.getString("text")))
                .list();
    }

    @Override
    public boolean reasonExists(UUID reasonId, String kind) {
        return jdbc.sql("SELECT COUNT(*) FROM azs_business.rejection_reason "
                + "WHERE id = :id AND kind = :kind AND active = 1")
                .param("id", RawUuid.toBytes(reasonId), Types.VARBINARY).param("kind", kind, Types.VARCHAR)
                .query(Integer.class).single() > 0;
    }
}
