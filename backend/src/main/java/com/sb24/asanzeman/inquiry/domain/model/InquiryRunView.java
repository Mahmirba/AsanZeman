package com.sb24.asanzeman.inquiry.domain.model;

import java.util.List;

/** A run with its inquiry results; the results are there once the run has finished. */
public record InquiryRunView(InquiryRun run, List<InquiryOutcome> outcomes) {

    public InquiryRunView {
        outcomes = List.copyOf(outcomes);
    }
}
