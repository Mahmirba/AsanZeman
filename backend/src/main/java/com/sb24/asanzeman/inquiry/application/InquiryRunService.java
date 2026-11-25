package com.sb24.asanzeman.inquiry.application;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryCooldownException;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRun;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRunNotFoundException;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRunView;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict;
import com.sb24.asanzeman.inquiry.domain.model.RunStatus;
import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunFinished;
import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunUseCase;
import com.sb24.asanzeman.inquiry.domain.port.in.RunInquiriesUseCase;
import com.sb24.asanzeman.inquiry.domain.port.out.InquiryRunRepositoryPort;
import com.sb24.asanzeman.inquiry.domain.port.out.RunEventsPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The lifecycle of a run (5.1): one at a time per request, in the background, with the answer polled. A second start
 * while one is running attaches to it; a run that never finished is recovered; a blocked customer waits out the
 * cooldown; an inquiry that passed lately is not asked again.
 *
 * <p>The run itself holds no database connection while the services answer (D24): the repository calls are single
 * statements around it. When it ends, {@link InquiryRunFinished} is published for the request module.
 */
public class InquiryRunService implements InquiryRunUseCase {

    private static final Logger log = LoggerFactory.getLogger(InquiryRunService.class);

    private final RunInquiriesUseCase chain;
    private final InquiryRunRepositoryPort runs;
    private final RunEventsPort events;
    private final Executor background;
    private final Clock clock;
    private final InquiryRunSettings settings;
    private final MeterRegistry metrics;

    public InquiryRunService(RunInquiriesUseCase chain, InquiryRunRepositoryPort runs, RunEventsPort events,
            Executor background, Clock clock, InquiryRunSettings settings, MeterRegistry metrics) {
        this.metrics = metrics;
        this.chain = chain;
        this.runs = runs;
        this.events = events;
        this.background = background;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public InquiryRunView start(UUID requestId, InquiryContext context) {
        Instant now = clock.instant();
        runs.markStale(requestId, now.minus(settings.staleAfter()));
        InquiryRun running = runs.findRunning(requestId).orElse(null);
        if (running != null) {
            return view(running); // a double click, or a second tab: attach to the run in progress
        }
        runs.findLatest(requestId).ifPresent(latest -> requireCooledDown(latest, now));

        InquiryRun run = InquiryRun.started(UUID.randomUUID(), requestId, now);
        if (!runs.insertRunning(run)) {
            return view(runs.findRunning(requestId).orElseThrow()); // another start won the race: attach to it
        }
        background.execute(() -> execute(run, context));
        return view(run);
    }

    @Override
    public java.util.Optional<com.sb24.asanzeman.inquiry.domain.model.Gender> genderOf(UUID requestId) {
        InquiryOutcome answer = runs.passedSince(requestId, Instant.EPOCH).get(InquiryType.SABT_AHVAL);
        return answer == null || answer.civil() == null ? java.util.Optional.empty()
                : java.util.Optional.of(answer.civil().gender());
    }

    @Override
    public boolean cleared(UUID requestId) {
        return runs.findLatest(requestId).filter(r -> r.status() == RunStatus.FINISHED)
                .filter(r -> r.decision() == InquiryVerdict.Decision.PROCEED).isPresent();
    }

    @Override
    public InquiryRunView get(UUID requestId, UUID runId) {
        return view(runs.find(requestId, runId).orElseThrow(InquiryRunNotFoundException::new));
    }

    /** Only a blocked run makes the customer wait; after a pass or a rejection there is nothing to wait for. */
    private void requireCooledDown(InquiryRun latest, Instant now) {
        if (latest.status() != RunStatus.FINISHED || latest.decision() != InquiryVerdict.Decision.BLOCKED) {
            return;
        }
        Instant mayRetryAt = latest.finishedAt().plus(settings.retryCooldown());
        if (mayRetryAt.isAfter(now)) {
            throw new InquiryCooldownException(Duration.between(now, mayRetryAt).toSeconds() + 1);
        }
    }

    private void execute(InquiryRun run, InquiryContext context) {
        InquiryVerdict verdict;
        try {
            var reusable = runs.passedSince(run.requestId(), clock.instant().minus(settings.reuseWindow()));
            verdict = chain.run(context, reusable);
        } catch (RuntimeException e) {
            // nothing could be decided, so nobody is rejected: the customer can try again
            log.error("inquiry run {} of request {} broke", run.id(), run.requestId(), e);
            verdict = new InquiryVerdict(InquiryVerdict.Decision.BLOCKED, null, List.of());
        }
        Instant finished = clock.instant();
        if (!runs.finish(run.id(), verdict.decision(), verdict.outcomes(), finished)) {
            log.warn("inquiry run {} was recovered as stale before it finished; its answer is dropped", run.id());
            return;
        }
        // the duration of UC-09 for the dashboard, by how the run ended
        metrics.timer("azs.inquiry.run", "decision", verdict.decision().name())
                .record(java.time.Duration.between(run.startedAt(), finished));
        InquiryOutcome rejection = verdict.rejection();
        events.publish(new InquiryRunFinished(run.requestId(), run.id(), verdict.decision(),
                rejection == null ? null : rejection.messageKey()));
    }

    private InquiryRunView view(InquiryRun run) {
        return new InquiryRunView(run, run.status() == RunStatus.FINISHED ? runs.results(run.id()) : List.of());
    }
}
