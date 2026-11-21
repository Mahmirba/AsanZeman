package com.sb24.asanzeman.request.domain.model;

/**
 * One branch as Yaghut describes it (4.4, D32). {@code code} is <b>always a string</b>, exactly as Yaghut sends it:
 * it is matched with a bank user's branch code character for character, with no zero-stripping.
 */
public record BranchData(String code, String name, String city, String address, String tel, String fax,
        String zipCode) {

    public BranchData {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("a branch needs a code");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a branch needs a name");
        }
    }
}
