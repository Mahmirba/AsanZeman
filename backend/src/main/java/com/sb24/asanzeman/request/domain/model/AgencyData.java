package com.sb24.asanzeman.request.domain.model;

import java.util.regex.Pattern;

/**
 * What the admin enters for an agency: its Yaghut customer number (the login identity, D35), the 4-digit code that goes
 * into every tracking code, its name, whether it may register requests, and the mobile number told about revocations
 * (optional).
 */
public record AgencyData(String cif, String code, String name, boolean active, String mobile) {

    private static final Pattern CODE = Pattern.compile("[0-9]{4}");
    private static final Pattern MOBILE = Pattern.compile("09[0-9]{9}");

    public AgencyData {
        cif = cif == null ? "" : cif.trim();
        name = name == null ? "" : name.trim();
        mobile = mobile == null || mobile.isBlank() ? null : mobile.trim();
        if (cif.isEmpty() || cif.length() > 20) {
            throw new InvalidRequestDataException("the customer number of the agency is 1 to 20 characters");
        }
        if (code == null || !CODE.matcher(code).matches()) {
            throw new InvalidRequestDataException("the agency code is 4 digits");
        }
        if (name.isEmpty() || name.length() > 200) {
            throw new InvalidRequestDataException("the agency name is 1 to 200 characters");
        }
        if (mobile != null && !MOBILE.matcher(mobile).matches()) {
            throw new InvalidRequestDataException("the mobile number is 09xxxxxxxxx");
        }
    }
}
