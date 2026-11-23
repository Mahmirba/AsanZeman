package com.sb24.asanzeman.request.domain.model;

import java.time.Instant;

/**
 * A request in the branch's list (SRS review use case): the usual summary, who holds it and since when (every branch
 * user sees a held request, read-only, 4.1), the branch that handles it now, and what the branch recorded: the
 * rejection reason and comment, or the guarantee number.
 */
public record BranchRequestRow(RequestSummary summary, String lockedBy, Instant lockedAt, String handlingBranch,
        String rejectionReason, String rejectionComment, String guaranteeNumber) {

    /** The branch rule (4.4): the original branch, or the one that handles the request now. */
    public boolean canAct(BranchUser user) {
        return user.branchCode().equals(summary.branchCode()) || user.branchCode().equals(handlingBranch);
    }
}
