package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.BranchRequestDetails;
import com.sb24.asanzeman.request.domain.model.BranchRequestRow;
import com.sb24.asanzeman.request.domain.model.BranchUser;
import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.RejectionReason;
import com.sb24.asanzeman.request.domain.port.in.ReviewResult;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.MessageSource;

/** What the branch panel gets back (SRS review use case, 4.1, 4.4). */
final class BranchResponses {

    private BranchResponses() {
    }

    /**
     * A request in the branch's list. {@code lockedBy} and {@code lockedAt} say who took it with «اقدام»: every branch
     * user sees it, read-only. {@code canAct} is the branch rule (4.4): false when the user may only look.
     */
    record Row(RequestSummaryResponse request, String lockedBy, Instant lockedAt, boolean canAct,
            String handlingBranch, String rejectionReason, String rejectionComment, String guaranteeNumber) {

        static Row from(BranchRequestRow r, BranchUser user, MessageSource messages) {
            return new Row(RequestSummaryResponse.from(r.summary(), messages), r.lockedBy(), r.lockedAt(),
                    r.canAct(user), r.handlingBranch(), r.rejectionReason(), r.rejectionComment(),
                    r.guaranteeNumber());
        }
    }

    record Page(List<Row> items, long total, int page, int size) {

        static Page from(PageResult<BranchRequestRow> page, BranchUser user, MessageSource messages) {
            return new Page(page.items().stream().map(r -> Row.from(r, user, messages)).toList(), page.total(),
                    page.page(), page.size());
        }
    }

    record Companion(String fullName, String nationalCode, String birthDate, String passportNumber) {
    }

    record Details(Row request, List<Companion> companions, List<CollateralResponses.Allocation> collateral) {

        static Details from(BranchRequestDetails d, BranchUser user, MessageSource messages) {
            return new Details(Row.from(d.row(), user, messages),
                    d.companions().stream().map(c -> new Companion(c.fullName(), c.nationalCode(),
                            JalaliDate.fromGregorian(c.birthDate()).toSlashed(), c.passportNumber())).toList(),
                    d.collateral().stream().map(CollateralResponses.Allocation::from).toList());
        }
    }

    /** What an action left behind: the new status, and who holds the request now, if anyone. */
    record Result(UUID requestId, String status, String statusLabel, String heldBy, Instant heldSince) {

        static Result from(ReviewResult r, MessageSource messages) {
            return new Result(r.requestId(), r.status().name(),
                    RequestSummaryResponse.label(messages, "status", r.status()), r.heldBy(), r.heldSince());
        }
    }

    record Reason(UUID id, String text) {

        static Reason from(RejectionReason r) {
            return new Reason(r.id(), r.text());
        }
    }
}
