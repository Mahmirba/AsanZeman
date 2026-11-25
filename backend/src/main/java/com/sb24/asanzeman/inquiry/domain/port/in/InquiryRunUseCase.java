package com.sb24.asanzeman.inquiry.domain.port.in;

import com.sb24.asanzeman.inquiry.domain.model.Gender;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRunView;
import java.util.Optional;
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

    /**
     * The gender the civil registry (Sabte Ahval) gave for this request, from its newest passed Sabte Ahval answer; empty
     * when no run has passed it yet (D26).
     */
    Optional<Gender> genderOf(UUID requestId);

    /** @return whether the request's latest run has finished and every inquiry passed: it may go on to the collateral */
    boolean cleared(UUID requestId);

    /** @throws com.sb24.asanzeman.inquiry.domain.model.InquiryRunNotFoundException unknown, or another request's */
    InquiryRunView get(UUID requestId, UUID runId);
}
