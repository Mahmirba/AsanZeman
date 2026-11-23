package com.sb24.asanzeman.inquiry.domain.port.in;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRunView;
import java.util.UUID;

/** Starts and reads the background runs of the inquiry chain (5.1). The caller has already checked who owns the request. */
public interface InquiryRunUseCase {

    /**
     * Starts a run in the background, or returns the run already in progress for this request, so a double click never
     * fails. A passed inquiry is reused for the reuse window.
     *
     * @throws com.sb24.asanzeman.inquiry.domain.model.InquiryCooldownException a blocked run ended too recently
     */
    InquiryRunView start(UUID requestId, InquiryContext context);

    /** @throws com.sb24.asanzeman.inquiry.domain.model.InquiryRunNotFoundException unknown, or another request's */
    InquiryRunView get(UUID requestId, UUID runId);
}
