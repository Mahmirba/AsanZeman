package com.sb24.asanzeman.request.domain.model;

/** What a request asks for (4): a guarantee, or the revocation of an issued one (linked by {@code parent_request_id}). */
public enum RequestType {
    ISSUANCE,
    REVOCATION
}
