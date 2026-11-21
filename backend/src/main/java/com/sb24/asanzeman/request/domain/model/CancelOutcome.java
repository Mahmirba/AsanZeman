package com.sb24.asanzeman.request.domain.model;

/** What the agency's single «ثبت درخواست ابطال» action did (4.2, D11). */
public enum CancelOutcome {
    /** Before «اقدام»: the request was cancelled at once (لغو). */
    CANCELLED,
    /** From «اقدام» on: a revocation request was created (ابطال). Not built yet. */
    REVOCATION_REQUESTED
}
