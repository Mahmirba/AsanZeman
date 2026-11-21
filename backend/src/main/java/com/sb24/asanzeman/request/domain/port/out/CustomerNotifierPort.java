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
}
