package com.sb24.asanzeman.request.domain.port.out;

import java.util.UUID;

/** Tells the customer what happened to their request (6.4). Every method joins the caller's transaction. */
public interface CustomerNotifierPort {

    /**
     * SMS event 1 (SRS UC-AZS-8, step 15): a request was registered, so the customer should complete it. If the
     * registration rolls back, no message is sent.
     *
     * @param requestId  the new request, for tracing the message
     * @param mobile     the customer's mobile number as saved with the request
     * @param agencyName the agency's name, shown in the text
     */
    void customerRegistered(UUID requestId, String mobile, String agencyName);

    /**
     * SMS event 6 (agreed 1405/08/23): the agency cancelled the request before «اقدام».
     *
     * @param sentDate the day of the message, Jalali {@code yyyy/MM/dd}
     */
    void requestCancelled(UUID requestId, String mobile, String agencyName, String sentDate);

    /** SMS event 8: an inquiry rejected the request. */
    void requestRejectedByInquiry(UUID requestId, String mobile, String agencyName, String sentDate);

    /** SMS event 7: the customer did not complete the request within 7 days and it was rejected (the scheduler, Phase 8). */
    void requestExpired(UUID requestId, String mobile, String agencyName, String sentDate);

    /** SMS event 4: the branch rejected the request. The salutation line is left out: the registry gender is not at hand here (6.4). */
    void requestRejected(UUID requestId, String mobile, String agencyName, String branchName, String sentDate);

    /** SMS event 3: the branch recorded the guarantee number, so the guarantee is issued. */
    void guaranteeIssued(UUID requestId, String mobile, String agencyName, String branchName, String sentDate);

    /** SMS event 5: the branch revoked the guarantee. */
    void guaranteeRevoked(UUID requestId, String mobile, String agencyName, String branchName, String sentDate);

    /** SMS event 7: the branch did not approve the revocation request. */
    void revocationRejected(UUID requestId, String mobile, String agencyName, String branchName, String sentDate);

    /** SMS to the agency: its revocation request was completed. Does nothing when the agency has no mobile number. */
    void agencyRevocationCompleted(UUID requestId, String agencyMobile, String trackingCode, String branchName,
            String sentDate);

    /** SMS to the agency: its revocation request was not approved. Does nothing when the agency has no mobile number. */
    void agencyRevocationRejected(UUID requestId, String agencyMobile, String trackingCode, String branchName,
            String sentDate);
}
