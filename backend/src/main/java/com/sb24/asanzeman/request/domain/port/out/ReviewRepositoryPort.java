package com.sb24.asanzeman.request.domain.port.out;

import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.Review;
import com.sb24.asanzeman.request.domain.model.ReviewLock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The branch's view of the {@code request} table (4.1): the request with its ownership lock. Every method joins the caller's transaction. */
public interface ReviewRepositoryPort {

    /**
     * Reads the request with its lock and the branch that handles it now, and locks the row without waiting
     * ({@code FOR UPDATE NOWAIT}); this is what makes taking a request atomic.
     *
     * @throws com.sb24.asanzeman.request.domain.model.RequestBusyException another transaction holds the row
     */
    Optional<Review> findForUpdate(UUID requestId);

    /** Saves the new status and the ownership lock ({@code null} clears it) of a request whose row this transaction holds. */
    void save(UUID requestId, RequestStatus status, ReviewLock lock, Instant at);

    /** Records the reason and the comment of a rejection. */
    void recordRejection(UUID requestId, UUID reasonId, String comment);

    /** Records the guarantee number the branch got from Farangin, and when. */
    void recordIssue(UUID requestId, String guaranteeNumber, Instant at);

    /** The requests in review that nobody has touched since {@code before}. */
    List<UUID> idleReviews(Instant before);

    /** @return whether an active issuance rejection reason has this id */
    boolean issuanceReasonExists(UUID reasonId);

    /** The admin's active reasons for rejecting an issuance request, in their order. */
    List<com.sb24.asanzeman.request.domain.model.RejectionReason> issuanceReasons();
}
