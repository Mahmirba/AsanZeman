package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.BranchRequestDetails;
import com.sb24.asanzeman.request.domain.model.BranchRequestRow;
import com.sb24.asanzeman.request.domain.model.BranchSearchCriteria;
import com.sb24.asanzeman.request.domain.model.BranchUser;
import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.RejectionReason;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

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
     * Every request the search finds, in pages for the sink (D42), so a large report never sits in memory. The same
     * scope as {@link #search}.
     */
    void export(BranchUser user, BranchSearchCriteria criteria, Consumer<List<BranchRequestRow>> sink);

    /**
     * One request with its companions and collateral.
     *
     * @throws com.sb24.asanzeman.request.domain.model.RequestNotFoundException unknown, not yet sent to a branch, or a
     *         branch user whose branch may not see it
     */
    BranchRequestDetails get(BranchUser user, UUID requestId);

    /**
     * The reasons a rejection may give, from the admin's list for that kind of request.
     *
     * @param kind {@code ISSUANCE} or {@code REVOCATION}
     */
    List<RejectionReason> rejectionReasons(String kind);
}
