package com.sb24.asanzeman.request.domain.model;

import java.util.UUID;

/**
 * The branch or admin user who works on a request (4.1, 4.4): their id, the {@code accountName} that other users see as
 * the lock holder (D33), and the branch they work at, fixed at login (D33).
 */
public record BranchUser(UUID id, String name, String branchCode, boolean admin) {

    public BranchUser {
        if (id == null || name == null || name.isBlank() || branchCode == null || branchCode.isBlank()) {
            throw new IllegalArgumentException("a branch user needs an id, a name and a branch");
        }
    }
}
