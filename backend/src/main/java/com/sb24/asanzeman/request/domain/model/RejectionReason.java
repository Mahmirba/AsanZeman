package com.sb24.asanzeman.request.domain.model;

import java.util.UUID;

/** One entry of the admin's list of reasons a branch may reject an issuance request with. */
public record RejectionReason(UUID id, String text) {
}
