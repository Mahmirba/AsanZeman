package com.sb24.asanzeman.request.domain.port.out;

import com.sb24.asanzeman.request.domain.model.BranchData;
import com.sb24.asanzeman.request.domain.model.BranchSyncResult;
import java.time.Instant;
import java.util.List;

/** The {@code branch} table. Branch data is never cached (D45). */
public interface BranchRepositoryPort {

    /**
     * Applies a full list from Yaghut in one transaction (4.4): a new branch is inserted with
     * {@code parent_branch = branch_code}; an existing one has its details updated and is reactivated, but its
     * {@code parent_branch} is never touched; a branch missing from the list is set inactive, never deleted.
     */
    BranchSyncResult apply(List<BranchData> fetched, Instant now);

    boolean any();

    /** Every branch with the one that handles it, for the integrity check (4.4). */
    java.util.List<com.sb24.asanzeman.request.domain.model.BranchLink> links();

    /** @return true when the branch exists, is active and is not closed (4.4): new requests may use it */
    boolean isOpen(String branchCode);

    /** @return the branch's name, whether or not it is closed (a request keeps its original branch) */
    java.util.Optional<String> nameOf(String branchCode);

    /**
     * Open branches whose name or code starts with {@code prefix} (blank means all), ordered by name, at most
     * {@code limit}.
     */
    java.util.List<com.sb24.asanzeman.request.domain.model.BranchSummary> searchOpen(String prefix, int limit);
}
