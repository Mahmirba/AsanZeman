package com.sb24.asanzeman.auth.domain.model;

import java.util.UUID;

/** A bank user as stored in {@code app_user} (D33). */
public record AppUser(UUID id, AccountName accountName, String branchCode, Role role) {
}
