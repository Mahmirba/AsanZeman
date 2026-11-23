package com.sb24.asanzeman.request.domain.model;

import java.util.UUID;

/** An agency as the admin sees it. */
public record AdminAgency(UUID id, AgencyData data) {
}
