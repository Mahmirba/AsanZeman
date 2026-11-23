package com.sb24.asanzeman.request.domain.model;

import java.util.UUID;

/**
 * An agency as the request module needs it: its 4-digit code goes into the tracking code, its name into the SMS, and
 * its mobile number (optional, may be null) receives the SMS about its revocation requests.
 */
public record AgencyInfo(UUID id, String code, String name, boolean active, String mobile) {

    public AgencyInfo(UUID id, String code, String name, boolean active) {
        this(id, code, name, active, null);
    }
}
