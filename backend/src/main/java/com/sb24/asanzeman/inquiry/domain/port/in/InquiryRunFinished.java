package com.sb24.asanzeman.inquiry.domain.port.in;

import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict;
import java.util.UUID;

/**
 * Published when a run ends. The request module listens: a {@code REJECT} rejects the request with
 * {@code rejectionMessageKey}. The inquiry module never calls the request module back (no cycle).
 *
 * @param rejectionMessageKey the SRS text for the customer; only set for {@code REJECT}
 */
public record InquiryRunFinished(UUID requestId, UUID runId, InquiryVerdict.Decision decision,
        String rejectionMessageKey) {
}
