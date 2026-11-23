package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.inquiry.domain.model.InquiryRunView;
import java.util.UUID;

/** The customer's inquiries for one of their own requests (5.1): start a run, then read its progress. */
public interface CustomerInquiryUseCase {

    /**
     * Starts the inquiries in the background, or attaches to the run already in progress.
     *
     * @throws com.sb24.asanzeman.request.domain.model.RequestNotFoundException     not this customer's request
     * @throws com.sb24.asanzeman.request.domain.model.RequestNotInquirableException the request is not waiting for them
     * @throws com.sb24.asanzeman.inquiry.domain.model.InquiryCooldownException     a blocked run ended too recently
     */
    InquiryRunView start(String nationalCode, UUID requestId);

    /**
     * @throws com.sb24.asanzeman.request.domain.model.RequestNotFoundException         not this customer's request
     * @throws com.sb24.asanzeman.inquiry.domain.model.InquiryRunNotFoundException      no such run for the request
     */
    InquiryRunView get(String nationalCode, UUID requestId, UUID runId);
}
