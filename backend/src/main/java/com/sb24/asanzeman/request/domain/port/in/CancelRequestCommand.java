package com.sb24.asanzeman.request.domain.port.in;

import java.util.UUID;

/**
 * The agency's «ثبت درخواست ابطال» action on one of its requests (4.2).
 *
 * @param agencyName the agency, as the customer's SMS names it
 * @param actor      the person who acted for the agency, as the audit log names them
 */
public record CancelRequestCommand(UUID agencyId, String agencyName, UUID requestId, String clientIp,
        String userAgent, String actor) {

    /** The actor is the agency itself when no person is known. */
    public CancelRequestCommand(UUID agencyId, String agencyName, UUID requestId, String clientIp, String userAgent) {
        this(agencyId, agencyName, requestId, clientIp, userAgent, agencyName);
    }
}
