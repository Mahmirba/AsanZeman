package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.BranchRequestDetails;
import com.sb24.asanzeman.request.domain.model.BranchRequestRow;
import com.sb24.asanzeman.request.domain.model.BranchSearchCriteria;
import com.sb24.asanzeman.request.domain.model.BranchUser;
import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.RejectionReason;
import java.util.List;
import java.util.UUID;

/** The branch user's inbox, search and details (SRS review use case, 4.4). */
public interface BranchQueryUseCase {

    int MAX_PAGE_SIZE = 200;

    /**
     * The branch's requests, newest first. A branch user sees their own branch's and those of the branches it replaced;
     * an admin sees every branch.
     *
     * @param criteria its {@code branchCode} is ignored: it is taken from the user
     * @throws com.sb24.asanzeman.request.domain.model.InvalidRequestDataException a bad page or size
     */
    PageResult<BranchRequestRow> search(BranchUser user, BranchSearchCriteria criteria, int page, int size);

    /**
     * One request with its companions and collateral.
     *
     * @throws com.sb24.asanzeman.request.domain.model.RequestNotFoundException unknown, not yet sent to a branch, or a
     *         branch user whose branch may not see it
     */
    BranchRequestDetails get(BranchUser user, UUID requestId);

    /** The reasons a rejection may give, from the admin's list. */
    List<RejectionReason> rejectionReasons();
}
