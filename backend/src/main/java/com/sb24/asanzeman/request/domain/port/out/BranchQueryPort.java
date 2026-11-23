package com.sb24.asanzeman.request.domain.port.out;

import com.sb24.asanzeman.request.domain.model.BranchRequestRow;
import com.sb24.asanzeman.request.domain.model.BranchSearchCriteria;
import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The branch's read side: the inbox and search, and one request with its companions. Newest request first. */
public interface BranchQueryPort {

    /** @param page starting at 0 */
    PageResult<BranchRequestRow> search(BranchSearchCriteria criteria, int page, int size);

    Optional<BranchRequestRow> find(UUID requestId);

    List<Companion> companions(UUID requestId);
}
