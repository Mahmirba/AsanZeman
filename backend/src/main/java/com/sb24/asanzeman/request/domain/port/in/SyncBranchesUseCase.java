package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.BranchSyncResult;

/** Brings the {@code branch} table in line with Yaghut (4.4, D32). Triggered by the scheduler. */
public interface SyncBranchesUseCase {

    /**
     * Reads every page from Yaghut first, then applies the changes in one transaction: if Yaghut fails, nothing
     * changes and the next run tries again.
     *
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException Yaghut failed, or answered with no
     *         branches at all (which would deactivate every branch)
     */
    BranchSyncResult sync();

    /** @return {@code true} when the table already has branches, so the startup sync can be skipped */
    boolean hasBranches();
}
