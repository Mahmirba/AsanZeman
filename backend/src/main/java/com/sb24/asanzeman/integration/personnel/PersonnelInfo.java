package com.sb24.asanzeman.integration.personnel;

/**
 * What personnelInfo says about a bank user (6.2, D33). Only {@code userInfo.abrnchcod} is the user's branch;
 * {@code abrnchcodmain} and {@code prAcvBranch} are ignored.
 *
 * @param branchCode       {@code abrnchcod}, as a string with no zero stripping (D32)
 * @param prsnCode         personnel code
 * @param personelUserName the user name personnelInfo knows
 * @param branchName       {@code aBranchName}
 * @param position         the user's position
 * @param job              the user's job
 */
public record PersonnelInfo(
        String branchCode,
        String prsnCode,
        String personelUserName,
        String branchName,
        String position,
        String job) {
}
