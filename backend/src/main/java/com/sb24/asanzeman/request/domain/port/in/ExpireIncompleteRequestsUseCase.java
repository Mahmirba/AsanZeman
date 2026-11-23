package com.sb24.asanzeman.request.domain.port.in;

/** The 7-day rule: a request the customer has not completed in time is rejected, and the customer is told by SMS. */
public interface ExpireIncompleteRequestsUseCase {

    /** @return how many requests were rejected */
    int expireIncomplete();
}
