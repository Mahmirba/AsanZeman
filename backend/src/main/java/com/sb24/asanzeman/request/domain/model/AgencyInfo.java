package com.sb24.asanzeman.request.domain.model;

import java.util.UUID;

/** An agency as registration needs it: its 4-digit code goes into the tracking code, its name into the SMS. */
public record AgencyInfo(UUID id, String code, String name, boolean active) {
}
