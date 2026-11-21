package com.sb24.asanzeman.integration.yaghut;

import java.util.List;

/** One page of Yaghut {@code branches}: {@code totalRecord} and the branches of this page (5.4, 4.4). */
public record YaghutBranchPage(long totalRecord, List<Branch> branches) {

    /** {@code branchCode} is <b>always a string</b>, exactly as Yaghut sends it (D32). */
    public record Branch(String branchCode, String name, String city, String address, String tel, String fax,
            String zipCode) {
    }

    public YaghutBranchPage {
        branches = List.copyOf(branches);
    }
}
