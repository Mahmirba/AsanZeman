package com.sb24.asanzeman.request.domain.model;

/** What the admin enters for a rejection reason: for issuance or revocation requests, its text, order and whether it is offered. */
public record ReasonData(String kind, String text, boolean active, int sortOrder) {

    public static final int TEXT_MAX = 500;

    public ReasonData {
        text = text == null ? "" : text.trim();
        if (!"ISSUANCE".equals(kind) && !"REVOCATION".equals(kind)) {
            throw new InvalidRequestDataException("the kind of reason is ISSUANCE or REVOCATION");
        }
        if (text.isEmpty() || text.length() > TEXT_MAX) {
            throw new InvalidRequestDataException("the reason text is 1 to " + TEXT_MAX + " characters");
        }
        if (sortOrder < 0 || sortOrder > 99999) {
            throw new InvalidRequestDataException("the order is 0 to 99999");
        }
    }
}
