package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.model.GuaranteeNumber;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestBusyException;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.Review;
import com.sb24.asanzeman.request.domain.port.in.BranchCommand;
import com.sb24.asanzeman.request.domain.port.in.BranchReviewUseCase;
import com.sb24.asanzeman.request.domain.port.in.ReviewResult;
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort;
import com.sb24.asanzeman.request.domain.port.out.ReviewRepositoryPort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The branch's work on a request (4.1). Each action runs in one short transaction: the row is read with
 * {@code FOR UPDATE NOWAIT}, the domain checks the branch rule, the status and the ownership, the result is saved and
 * audited, and the transaction ends, so the row lock is gone and only the ownership columns remain. Two users taking the
 * same request at the same instant cannot both win: one gets the row, the other a 409.
 *
 * <p>The customer's SMS for a rejection or an issue goes into the outbox in the same transaction, so it is sent only
 * if the change commits.
 */
@Service
@Transactional
public class BranchReviewService implements BranchReviewUseCase {

    static final String ENTITY = "REQUEST";
    static final int COMMENT_MAX = 200;
    private static final Logger log = LoggerFactory.getLogger(BranchReviewService.class);

    private final ReviewRepositoryPort reviews;
    private final CustomerNotifierPort notifier;
    private final AgencyRegistryPort agencies;
    private final BranchRepositoryPort branches;
    private final RecordAuditUseCase audit;
    private final Clock clock;
    private final ReviewSettings settings;

    public BranchReviewService(ReviewRepositoryPort reviews, CustomerNotifierPort notifier, AgencyRegistryPort agencies,
            BranchRepositoryPort branches, RecordAuditUseCase audit, Clock clock, ReviewSettings settings) {
        this.reviews = reviews;
        this.notifier = notifier;
        this.agencies = agencies;
        this.branches = branches;
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
        if (reasonId == null || !reviews.issuanceReasonExists(reasonId)) {
            throw new InvalidRequestDataException("the rejection reason is not one of the active reasons");
        }
        Applied applied = apply(command, "REJECT", (review, now) -> review.reject(command.user()), text);
        reviews.recordRejection(command.requestId(), reasonId, text);
        Request request = applied.after().request();
        notifier.requestRejected(request.id(), request.customer().mobile(), agencyName(request),
                branchName(request), today());
        return applied.result();
    }

    @Override
    public ReviewResult issue(BranchCommand command, String guaranteeNumber) {
        GuaranteeNumber number = new GuaranteeNumber(guaranteeNumber);
        Applied applied = apply(command, "ISSUE", (review, now) -> review.issue(command.user()), number.value());
        reviews.recordIssue(command.requestId(), number.value(), clock.instant());
        Request request = applied.after().request();
        notifier.guaranteeIssued(request.id(), request.customer().mobile(), agencyName(request), branchName(request),
                today());
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

    private Applied apply(BranchCommand command, String action, BiFunction<Review, Instant, Review> step,
            String reason) {
        Review before = reviews.findForUpdate(command.requestId()).orElseThrow(RequestNotFoundException::new);
        Instant now = clock.instant();
        Review after = step.apply(before, now);
        reviews.save(command.requestId(), after.request().status(), after.lock(), now);
        var user = command.user();
        audit.record(new AuditEntry(user.id(), user.name(), user.admin() ? "ADMIN" : "BRANCH", ENTITY,
                command.requestId(), action, before.request().status().name(), after.request().status().name(), reason,
                command.clientIp(), command.userAgent(), null));
        return new Applied(before, after);
    }

    private String agencyName(Request request) {
        return agencies.find(request.agencyId()).map(a -> a.name()).orElse("");
    }

    private String branchName(Request request) {
        return branches.nameOf(request.branchCode()).orElse(request.branchCode());
    }

    private String today() {
        return JalaliDate.fromGregorian(clock.instant().atZone(TrackingCodeGenerator.TEHRAN).toLocalDate())
                .toSlashed();
    }
}
