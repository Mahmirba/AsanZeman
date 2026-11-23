package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.model.BranchRequestDetails;
import com.sb24.asanzeman.request.domain.model.BranchRequestRow;
import com.sb24.asanzeman.request.domain.model.BranchSearchCriteria;
import com.sb24.asanzeman.request.domain.model.BranchUser;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.RejectionReason;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.port.in.BranchQueryUseCase;
import com.sb24.asanzeman.request.domain.port.out.BranchQueryPort;
import com.sb24.asanzeman.request.domain.port.out.CollateralRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.ReviewRepositoryPort;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * The branch's read side (4.4). The branch rule decides what a user sees: the search is limited to their branch (and the
 * ones it replaced) in the query itself, and a request of another branch is a plain 404, as it is for the agency. An
 * admin sees every branch. Nothing here changes data.
 */
@Service
public class BranchQueryService implements BranchQueryUseCase {

    private final BranchQueryPort queries;
    private final CollateralRepositoryPort collateral;
    private final ReviewRepositoryPort reviews;

    public BranchQueryService(BranchQueryPort queries, CollateralRepositoryPort collateral,
            ReviewRepositoryPort reviews) {
        this.queries = queries;
        this.collateral = collateral;
        this.reviews = reviews;
    }

    @Override
    public PageResult<BranchRequestRow> search(BranchUser user, BranchSearchCriteria c, int page, int size) {
        if (page < 0) {
            throw new InvalidRequestDataException("the page starts at 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestDataException("the page size is 1 to " + MAX_PAGE_SIZE);
        }
        var scoped = new BranchSearchCriteria(user.admin() ? null : user.branchCode(), c.statuses(), c.customerCode(),
                c.customerCif(), c.trackingCode(), c.registeredFrom(), c.registeredTo());
        return queries.search(scoped, page, size);
    }

    @Override
    public void export(BranchUser user, BranchSearchCriteria criteria, Consumer<List<BranchRequestRow>> sink) {
        int page = 0;
        PageResult<BranchRequestRow> current;
        do {
            current = search(user, criteria, page++, MAX_PAGE_SIZE);
            if (!current.items().isEmpty()) {
                sink.accept(current.items());
            }
        } while ((long) page * MAX_PAGE_SIZE < current.total() && !current.items().isEmpty());
    }

    @Override
    public BranchRequestDetails get(BranchUser user, UUID requestId) {
        BranchRequestRow row = queries.find(requestId).filter(r -> user.admin() || r.canAct(user))
                .orElseThrow(RequestNotFoundException::new);
        return new BranchRequestDetails(row, queries.companions(requestId), collateral.find(requestId));
    }

    @Override
    public List<RejectionReason> rejectionReasons(String kind) {
        if (!"ISSUANCE".equals(kind) && !"REVOCATION".equals(kind)) {
            throw new InvalidRequestDataException("the kind of request is ISSUANCE or REVOCATION");
        }
        return reviews.reasons(kind);
    }
}
