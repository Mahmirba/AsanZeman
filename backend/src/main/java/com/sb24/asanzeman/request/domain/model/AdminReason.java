package com.sb24.asanzeman.request.domain.model;

import java.util.UUID;

/** A rejection reason as the admin sees it, active or not. */
public record AdminReason(UUID id, ReasonData data) {
}
