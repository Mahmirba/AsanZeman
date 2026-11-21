package com.sb24.asanzeman.request.domain.port.out;

/** Tells the customer a request was registered for them, so they can complete it (SRS UC-AZS-8, step 15). */
public interface RegistrationNotifierPort {

    /**
     * Joins the caller's transaction: if the registration rolls back, no message is sent.
     *
     * @param mobile       the customer's mobile number as saved with the request
     * @param agencyName   the agency's name, shown in the text
     * @param trackingCode the new request's tracking code
     */
    void customerRegistered(String mobile, String agencyName, String trackingCode);
}
