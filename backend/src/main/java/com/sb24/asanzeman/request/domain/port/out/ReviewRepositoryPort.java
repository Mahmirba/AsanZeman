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

    /**
     * @param kind {@code ISSUANCE} or {@code REVOCATION}: the two lists the admin keeps (SRS)
     * @return whether an active rejection reason of that kind has this id
     */
    boolean reasonExists(UUID reasonId, String kind);

    /** The admin's active reasons of that kind, in their order. */
    List<com.sb24.asanzeman.request.domain.model.RejectionReason> reasons(String kind);

    /**
     * Stores the revocation request of an issuance request (4.2): linked to it, and already held by {@code lock}'s
     * user when the issuance request is mid-work (D17).
     *
     * @param lock the holder, or {@code null} to leave it in the branch's queue
     */
    void insertRevocation(com.sb24.asanzeman.request.domain.model.Request revocation, UUID parentId, ReviewLock lock);
}
