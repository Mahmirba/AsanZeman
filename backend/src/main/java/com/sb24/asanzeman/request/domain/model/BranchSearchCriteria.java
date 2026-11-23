package com.sb24.asanzeman.request.domain.model;

import java.time.Instant;
import java.util.Set;

/**
 * What the branch looks for (SRS review use case). {@code branchCode} is the user's branch: they see the requests of
 * that branch and of the branches it replaced (4.4); {@code null} means every branch (an admin). Only the requests that
 * reached the branch can be listed, so {@code statuses} never holds {@code REGISTERED_AT_AGENCY}; an empty set means the
 * inbox: the three statuses of work in progress.
 */
public record BranchSearchCriteria(String branchCode, Set<RequestStatus> statuses, String customerCode,
        String customerCif, String trackingCode, Instant registeredFrom, Instant registeredTo) {

    /** The inbox: waiting, in review, in action. */
    public static final Set<RequestStatus> INBOX = Set.of(RequestStatus.PENDING_BRANCH_REVIEW,
            RequestStatus.UNDER_REVIEW, RequestStatus.UNDER_ACTION);

    public BranchSearchCriteria {
        statuses = statuses == null || statuses.isEmpty() ? INBOX : Set.copyOf(statuses);
        if (statuses.contains(RequestStatus.REGISTERED_AT_AGENCY)) {
            throw new InvalidRequestDataException("a request that has not reached the branch cannot be listed");
        }
        if (registeredFrom != null && registeredTo != null && registeredTo.isBefore(registeredFrom)) {
            throw new InvalidRequestDataException("the end of the date range is before its start");
        }
    }
}
