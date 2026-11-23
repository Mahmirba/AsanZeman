package com.sb24.asanzeman.inquiry.domain.model;

import java.util.List;

/**
 * How a run ended (5.1): {@code REJECT} (a NEGATIVE, the request is rejected with that message), {@code BLOCKED}
 * (no negative but something failed: the customer tries again) or {@code PROCEED} (everything passed or was skipped:
 * on to the collateral). {@code outcomes} has one entry per inquiry that took part, in the order they finished.
 */
public record InquiryVerdict(Decision decision, InquiryOutcome rejection, List<InquiryOutcome> outcomes) {

    public enum Decision {
        REJECT,
        BLOCKED,
        PROCEED
    }

    public InquiryVerdict {
        outcomes = List.copyOf(outcomes);
    }
}
