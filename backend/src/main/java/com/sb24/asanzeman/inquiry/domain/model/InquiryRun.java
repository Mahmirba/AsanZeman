package com.sb24.asanzeman.inquiry.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One run of the inquiry chain for a request (5.1). {@code decision} and {@code finishedAt} are set once it is
 * {@code FINISHED}.
 */
public record InquiryRun(UUID id, UUID requestId, RunStatus status, InquiryVerdict.Decision decision,
        Instant startedAt, Instant finishedAt) {

    public static InquiryRun started(UUID id, UUID requestId, Instant at) {
        return new InquiryRun(id, requestId, RunStatus.RUNNING, null, at, null);
    }
}
