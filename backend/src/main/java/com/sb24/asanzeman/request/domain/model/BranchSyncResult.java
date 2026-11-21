package com.sb24.asanzeman.request.domain.model;

/** What one branch sync changed (4.4): branches added, updated and set inactive because Yaghut no longer returns them. */
public record BranchSyncResult(int inserted, int updated, int deactivated) {

    public String summary() {
        return "inserted " + inserted + ", updated " + updated + ", deactivated " + deactivated;
    }
}
