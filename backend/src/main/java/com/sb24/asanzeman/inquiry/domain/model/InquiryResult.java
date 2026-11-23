package com.sb24.asanzeman.inquiry.domain.model;

/**
 * The four groups every inquiry falls into (5.1). Only {@code NEGATIVE} rejects a request: anything unclear is
 * {@code FAILED}, so nobody is rejected because of a technical fault.
 */
public enum InquiryResult {
    /** The service answered and the answer is fine. */
    PASSED,
    /** The service answered and the answer disqualifies the customer. */
    NEGATIVE,
    /** No usable answer: the customer may try again. */
    FAILED,
    /** The inquiry does not apply to this customer. */
    SKIPPED
}
