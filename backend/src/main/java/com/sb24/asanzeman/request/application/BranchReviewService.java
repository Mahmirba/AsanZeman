package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.model.GuaranteeNumber;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestBusyException;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.model.Review;
import com.sb24.asanzeman.request.domain.port.in.BranchCommand;
import com.sb24.asanzeman.request.domain.port.in.BranchReviewUseCase;
import com.sb24.asanzeman.request.domain.port.in.ReviewResult;
import com.sb24.asanzeman.request.domain.port.out.ReviewRepositoryPort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The branch's work on a request (4.1, 4.2). Each action runs in one short transaction: the row is read with
 * {@code FOR UPDATE NOWAIT}, the domain checks the branch rule, the status and the ownership, the result is saved and
 * audited, and the transaction ends, so the row lock is gone and only the ownership columns remain. Two users taking the
 * same request at the same instant cannot both win: one gets the row, the other a 409.
 *
 * <p>Issuance and revocation requests go through the same steps. A rejection takes its reason from the list for that kind;
 * completing a revocation also revokes the issuance request it belongs to, in the same transaction. The customer's SMS
 * goes into the outbox in the same transaction, so it is sent only if the change commits.
 */
@Service
@Transactional
public class BranchReviewService implements BranchReviewUseCase {

    static final String ENTITY = "REQUEST";
    static final int COMMENT_MAX = 200;
    private static final Logger log = LoggerFactory.getLogger(BranchReviewService.class);

    private final ReviewRepositoryPort reviews;
    private final BranchSms sms;
    private final RecordAuditUseCase audit;
    private final Clock clock;
    private final ReviewSettings settings;

    public BranchReviewService(ReviewRepositoryPort reviews, BranchSms sms, RecordAuditUseCase audit, Clock clock,
            ReviewSettings settings) {
        this.reviews = reviews;
        this.sms = sms;
        this.audit = audit;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public ReviewResult take(BranchCommand command) {
        return apply(command, "LOCK", (review, now) -> review.take(command.user(), now), null).result();
    }

    @Override
    public ReviewResult approve(BranchCommand command) {
        return apply(command, "APPROVE", (review, now) -> review.approve(command.user(), now), null).result();
    }

    @Override
    public ReviewResult reject(BranchCommand command, UUID reasonId, String comment) {
        String text = comment == null ? "" : comment.trim();
        if (text.isEmpty() || text.length() > COMMENT_MAX) {
            throw new InvalidRequestDataException("the rejection comment is required, at most " + COMMENT_MAX
                    + " characters");
        }
        Review before = load(command);
        boolean revocation = before.request().type() == RequestType.REVOCATION;
        // the reason comes from the list of this kind of request; if it is wrong the transaction ends with nothing saved
        if (reasonId == null || !reviews.reasonExists(reasonId, revocation ? "REVOCATION" : "ISSUANCE")) {
            throw new InvalidRequestDataException("the rejection reason is not one of the active reasons");
        }
        Applied applied = commit(command, before, revocation ? "REJECT_REVOCATION" : "REJECT",
                before.reject(command.user()), text);
        reviews.recordRejection(command.requestId(), reasonId, text);
        Request request = applied.after().request();
        if (revocation) {
            sms.revocationRejected(request, before.parentId());
        } else {
            sms.issuanceRejected(request);
        }
        return applied.result();
    }

    @Override
    public ReviewResult issue(BranchCommand command, String guaranteeNumber) {
        GuaranteeNumber number = new GuaranteeNumber(guaranteeNumber);
        Applied applied = apply(command, "ISSUE", (review, now) -> review.issue(command.user()), number.value());
        reviews.recordIssue(command.requestId(), number.value(), clock.instant());
        sms.issued(applied.after().request());
        return applied.result();
    }

    @Override
    public ReviewResult completeRevocation(BranchCommand command) {
        Applied applied = apply(command, "COMPLETE_REVOCATION",
                (review, now) -> review.completeRevocation(command.user()), null);
        UUID issuanceId = applied.before().parentId();
        if (issuanceId == null) {
            throw new RequestNotFoundException(); // a revocation always belongs to an issuance request
        }
        // the issuance request is revoked in the same transaction, whatever state it was in (4.2)
        Review issuance = reviews.findForUpdate(issuanceId).orElseThrow(RequestNotFoundException::new);
        Review revoked = issuance.revoked();
        Instant now = clock.instant();
        reviews.save(issuanceId, revoked.request().status(), null, now);
        record(command, issuanceId, "REVOKED", issuance.request().status().name(), revoked.request().status().name(),
                "by revocation " + command.requestId());
        sms.revoked(revoked.request(), applied.after().request());
        return applied.result();
    }

    @Override
    public ReviewResult abandon(BranchCommand command) {
        return apply(command, "ABANDON", (review, now) -> review.abandon(command.user()), null).result();
    }

    @Override
    public ReviewResult forceRelease(BranchCommand command, String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty() || text.length() > COMMENT_MAX) {
            throw new InvalidRequestDataException("the reason for a release is required, at most " + COMMENT_MAX
                    + " characters");
        }
        return apply(command, "FORCE_RELEASE", (review, now) -> review.forceRelease(), text).result();
    }

    @Override
    public int releaseIdle() {
        Instant now = clock.instant();
        int released = 0;
        for (UUID id : reviews.idleReviews(now.minus(settings.idleTimeout()))) {
            try {
                Review review = reviews.findForUpdate(id).orElse(null);
                Review after = review == null ? null : review.autoReleased(now, settings.idleTimeout()).orElse(null);
                if (after != null) {
                    reviews.save(id, after.request().status(), null, now);
                    audit.record(new AuditEntry(null, "system", "SYSTEM", ENTITY, id, "AUTO_RELEASE",
                            review.request().status().name(), after.request().status().name(),
                            "idle for " + settings.idleTimeout(), null, null, null));
                    released++;
                }
            } catch (RequestBusyException e) {
                log.info("request {} is busy, its idle review is left for the next run", id);
            }
        }
        return released;
    }

    /** What an action did: the review before and after, for the callers that need more than the result. */
    private record Applied(Review before, Review after) {

        ReviewResult result() {
            return new ReviewResult(after.request().id(), after.request().status(),
                    after.lock() == null ? null : after.lock().holderName(),
                    after.lock() == null ? null : after.lock().lockedAt());
        }
    }

    private Review load(BranchCommand command) {
        return reviews.findForUpdate(command.requestId()).orElseThrow(RequestNotFoundException::new);
    }

    private Applied apply(BranchCommand command, String action, BiFunction<Review, Instant, Review> step,
            String reason) {
        Review before = load(command);
        return commit(command, before, action, step.apply(before, clock.instant()), reason);
    }

    private Applied commit(BranchCommand command, Review before, String action, Review after, String reason) {
        reviews.save(command.requestId(), after.request().status(), after.lock(), clock.instant());
        record(command, command.requestId(), action, before.request().status().name(),
                after.request().status().name(), reason);
        return new Applied(before, after);
    }

    private void record(BranchCommand command, UUID requestId, String action, String from, String to, String reason) {
        var user = command.user();
        audit.record(new AuditEntry(user.id(), user.name(), user.admin() ? "ADMIN" : "BRANCH", ENTITY, requestId,
                action, from, to, reason, command.clientIp(), command.userAgent(), null));
    }
}
