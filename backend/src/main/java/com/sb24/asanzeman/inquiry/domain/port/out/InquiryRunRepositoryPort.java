package com.sb24.asanzeman.inquiry.domain.port.out;

import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRun;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Where the runs and their results are kept. At most one run per request is {@code RUNNING}. */
public interface InquiryRunRepositoryPort {

    /** A run still {@code RUNNING} that began before {@code before} is dead (a restart killed it): it becomes STALE. */
    void markStale(UUID requestId, Instant before);

    /** @return {@code false} when the request already has a running run, which is then the one to attach to */
    boolean insertRunning(InquiryRun run);

    Optional<InquiryRun> findRunning(UUID requestId);

    Optional<InquiryRun> findLatest(UUID requestId);

    Optional<InquiryRun> find(UUID requestId, UUID runId);

    /**
     * Ends a run and saves its results, only if it is still {@code RUNNING}.
     *
     * @return {@code false} when the run was no longer running (it went stale meanwhile): nothing is saved
     */
    boolean finish(UUID runId, InquiryVerdict.Decision decision, List<InquiryOutcome> outcomes, Instant at);

    List<InquiryOutcome> results(UUID runId);

    /** The newest {@code PASSED} outcome of each inquiry for this request that finished at or after {@code since}. */
    Map<InquiryType, InquiryOutcome> passedSince(UUID requestId, Instant since);
}
