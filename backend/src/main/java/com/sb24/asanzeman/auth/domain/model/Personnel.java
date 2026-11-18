package com.sb24.asanzeman.auth.domain.model;

/**
 * A bank user as personnelInfo describes them (6.2, D33). {@code branchCode} is the user's branch, fetched at every
 * login and fixed for the 8-hour session. The other fields are saved in {@code app_user} for the audit log.
 */
public record Personnel(
        AccountName accountName,
        String branchCode,
        String prsnCode,
        String personelUserName,
        String branchName,
        String position,
        String job) {

    public Personnel {
        if (accountName == null) {
            throw new IllegalArgumentException("accountName is required");
        }
        if (branchCode == null || branchCode.isBlank()) {
            throw new IllegalArgumentException("a bank user without a branch cannot log in");
        }
    }
}
