package com.sb24.asanzeman.request.domain.model;

/**
 * The statuses of a request (4, SRS). There is no {@code EXPIRED} (D19). The transitions between them are added
 * phase by phase, each with its own tests: registration (Phase 2) only creates {@code REGISTERED_AT_AGENCY}.
 */
public enum RequestStatus {
    /** ثبت درخواست در پنل آژانس: registered by the agency, waiting for the customer to complete it. */
    REGISTERED_AT_AGENCY,
    PENDING_BRANCH_REVIEW,
    UNDER_REVIEW,
    UNDER_ACTION,
    ISSUED,
    REJECTED,
    /** لغو شده: withdrawn by the agency before «اقدام». */
    CANCELLED,
    /** ابطال شده: revoked through a completed revocation request. */
    REVOKED,
    /** A revocation request whose work in Farangin is confirmed. */
    COMPLETED
}
