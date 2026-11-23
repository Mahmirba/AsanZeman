package com.sb24.asanzeman.inquiry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.inquiry.domain.model.CivilData;
import com.sb24.asanzeman.inquiry.domain.model.Gender;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryCooldownException;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRun;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRunNotFoundException;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict.Decision;
import com.sb24.asanzeman.inquiry.domain.model.RunStatus;
import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunFinished;
import com.sb24.asanzeman.inquiry.domain.port.in.RunInquiriesUseCase;
import com.sb24.asanzeman.inquiry.domain.port.out.InquiryRunRepositoryPort;
import com.sb24.asanzeman.inquiry.domain.port.out.RunEventsPort;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InquiryRunServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final InquiryRunSettings SETTINGS = new InquiryRunSettings(Duration.ofSeconds(60),
            Duration.ofSeconds(60), Duration.ofHours(1));
    private static final InquiryContext CONTEXT = new InquiryContext("1000000011", "09121234567", 1000, "0123");
    private static final UUID REQUEST = UUID.randomUUID();

    @Mock
    private RunInquiriesUseCase chain;
    @Mock
    private InquiryRunRepositoryPort runs;
    @Mock
    private RunEventsPort events;

    private final Executor now = Runnable::run; // the "background" runs at once, so the tests need no waiting
    private InquiryRunService service;

    @BeforeEach
    void setUp() {
        service = new InquiryRunService(chain, runs, events, now, new MutableClock(NOW), SETTINGS);
    }

    private static InquiryRun finished(Decision decision, Instant finishedAt) {
        return new InquiryRun(UUID.randomUUID(), REQUEST, RunStatus.FINISHED, decision, finishedAt.minusSeconds(5),
                finishedAt);
    }

    private void startAllowed() {
        when(runs.findRunning(REQUEST)).thenReturn(Optional.empty());
        when(runs.findLatest(REQUEST)).thenReturn(Optional.empty());
        when(runs.insertRunning(any())).thenReturn(true);
    }

    // ---- starting -------------------------------------------------------------------------------------------

    @Test
    void aNewRunIsRecordedStartedInTheBackgroundAndItsDecisionPublished() {
        startAllowed();
        var outcomes = List.of(InquiryOutcome.passed(InquiryType.SHAHKAR));
        when(runs.passedSince(REQUEST, NOW.minus(Duration.ofHours(1)))).thenReturn(Map.of());
        when(chain.run(CONTEXT, Map.of())).thenReturn(new InquiryVerdict(Decision.PROCEED, null, outcomes));
        when(runs.finish(any(), eq(Decision.PROCEED), eq(outcomes), eq(NOW))).thenReturn(true);

        var view = service.start(REQUEST, CONTEXT);

        verify(runs).markStale(REQUEST, NOW.minusSeconds(60));
        verify(events).publish(new InquiryRunFinished(REQUEST, view.run().id(), Decision.PROCEED, null));
        assertThat(view.run().status()).isEqualTo(RunStatus.RUNNING); // the answer to the caller is the start
        assertThat(view.outcomes()).isEmpty();
    }

    @Test
    void aRejectionPublishesTheMessageToShow() {
        startAllowed();
        var negative = InquiryOutcome.negative(InquiryType.BLACKLIST, "inquiry.blacklist.negative");
        when(chain.run(any(), any())).thenReturn(new InquiryVerdict(Decision.REJECT, negative, List.of(negative)));
        when(runs.finish(any(), eq(Decision.REJECT), any(), any())).thenReturn(true);

        var view = service.start(REQUEST, CONTEXT);

        verify(events).publish(new InquiryRunFinished(REQUEST, view.run().id(), Decision.REJECT,
                "inquiry.blacklist.negative"));
    }

    @Test
    void inquiriesThatPassedLatelyAreHandedToTheChainForReuse() {
        startAllowed();
        var passed = Map.of(InquiryType.SABT_AHVAL, InquiryOutcome.passed(InquiryType.SABT_AHVAL,
                new CivilData(Gender.MALE, LocalDate.of(1990, 5, 17))));
        when(runs.passedSince(REQUEST, NOW.minus(Duration.ofHours(1)))).thenReturn(passed);
        when(chain.run(CONTEXT, passed)).thenReturn(new InquiryVerdict(Decision.BLOCKED, null, List.of()));
        when(runs.finish(any(), any(), any(), any())).thenReturn(true);

        service.start(REQUEST, CONTEXT);

        verify(chain).run(CONTEXT, passed);
    }

    @Test
    void aSecondStartWhileOneIsRunningAttachesToItWithoutStartingAnother() {
        var running = InquiryRun.started(UUID.randomUUID(), REQUEST, NOW.minusSeconds(3));
        when(runs.findRunning(REQUEST)).thenReturn(Optional.of(running));

        var view = service.start(REQUEST, CONTEXT);

        assertThat(view.run()).isEqualTo(running);
        verify(runs, never()).insertRunning(any());
        verifyNoInteractions(chain, events);
    }

    @Test
    void whenAnotherStartWinsTheRaceThisOneAttachesToTheWinner() {
        var winner = InquiryRun.started(UUID.randomUUID(), REQUEST, NOW);
        when(runs.findRunning(REQUEST)).thenReturn(Optional.empty(), Optional.of(winner));
        when(runs.findLatest(REQUEST)).thenReturn(Optional.empty());
        when(runs.insertRunning(any())).thenReturn(false);

        assertThat(service.start(REQUEST, CONTEXT).run()).isEqualTo(winner);
        verifyNoInteractions(chain, events);
    }

    // ---- the cooldown ---------------------------------------------------------------------------------------

    @Test
    void aBlockedCustomerWaitsOutTheCooldownAndIsToldHowLong() {
        when(runs.findRunning(REQUEST)).thenReturn(Optional.empty());
        when(runs.findLatest(REQUEST)).thenReturn(Optional.of(finished(Decision.BLOCKED, NOW.minusSeconds(20))));

        assertThatThrownBy(() -> service.start(REQUEST, CONTEXT)).isInstanceOf(InquiryCooldownException.class)
                .satisfies(e -> assertThat(((InquiryCooldownException) e).args()).containsExactly(41L));
        verify(runs, never()).insertRunning(any());
    }

    @Test
    void afterTheCooldownARetryStarts() {
        startAllowed();
        when(runs.findLatest(REQUEST)).thenReturn(Optional.of(finished(Decision.BLOCKED, NOW.minusSeconds(60))));
        when(chain.run(any(), any())).thenReturn(new InquiryVerdict(Decision.PROCEED, null, List.of()));
        when(runs.finish(any(), any(), any(), any())).thenReturn(true);

        service.start(REQUEST, CONTEXT);

        verify(chain).run(any(), any());
    }

    @Test
    void onlyABlockedRunMakesTheCustomerWait() {
        for (Decision decision : List.of(Decision.PROCEED, Decision.REJECT)) {
            when(runs.findRunning(REQUEST)).thenReturn(Optional.empty());
            when(runs.findLatest(REQUEST)).thenReturn(Optional.of(finished(decision, NOW.minusSeconds(1))));
            when(runs.insertRunning(any())).thenReturn(true);
            when(chain.run(any(), any())).thenReturn(new InquiryVerdict(Decision.PROCEED, null, List.of()));
            when(runs.finish(any(), any(), any(), any())).thenReturn(true);

            service.start(REQUEST, CONTEXT); // would throw if the cooldown applied
        }
    }

    @Test
    void aRunThatWentStaleDoesNotMakeTheCustomerWait() {
        startAllowed();
        var stale = new InquiryRun(UUID.randomUUID(), REQUEST, RunStatus.STALE, null, NOW.minusSeconds(100), null);
        when(runs.findLatest(REQUEST)).thenReturn(Optional.of(stale));
        when(chain.run(any(), any())).thenReturn(new InquiryVerdict(Decision.PROCEED, null, List.of()));
        when(runs.finish(any(), any(), any(), any())).thenReturn(true);

        service.start(REQUEST, CONTEXT);

        verify(chain).run(any(), any());
    }

    // ---- when the run itself goes wrong -----------------------------------------------------------------------

    @Test
    void aChainThatBreaksBlocksTheCustomerInsteadOfRejectingAnyone() {
        startAllowed();
        when(chain.run(any(), any())).thenThrow(new IllegalStateException("boom"));
        when(runs.finish(any(), eq(Decision.BLOCKED), eq(List.of()), any())).thenReturn(true);

        var view = service.start(REQUEST, CONTEXT);

        verify(events).publish(new InquiryRunFinished(REQUEST, view.run().id(), Decision.BLOCKED, null));
    }

    @Test
    void aRunRecoveredAsStaleMeanwhileSavesAndPublishesNothing() {
        startAllowed();
        when(chain.run(any(), any())).thenReturn(new InquiryVerdict(Decision.PROCEED, null, List.of()));
        when(runs.finish(any(), any(), any(), any())).thenReturn(false);

        service.start(REQUEST, CONTEXT);

        verifyNoInteractions(events);
    }

    // ---- reading --------------------------------------------------------------------------------------------

    @Test
    void aFinishedRunIsReadWithItsResultsAndARunningOneWithout() {
        var done = finished(Decision.PROCEED, NOW);
        var results = List.of(InquiryOutcome.passed(InquiryType.SHAHKAR));
        when(runs.find(REQUEST, done.id())).thenReturn(Optional.of(done));
        when(runs.results(done.id())).thenReturn(results);
        var running = InquiryRun.started(UUID.randomUUID(), REQUEST, NOW);
        when(runs.find(REQUEST, running.id())).thenReturn(Optional.of(running));

        assertThat(service.get(REQUEST, done.id()).outcomes()).isEqualTo(results);
        assertThat(service.get(REQUEST, running.id()).outcomes()).isEmpty();
        verify(runs, never()).results(running.id());
    }

    @Test
    void onlyAFinishedRunThatPassedClearsTheRequestForTheCollateral() {
        when(runs.findLatest(REQUEST)).thenReturn(Optional.of(finished(Decision.PROCEED, NOW)),
                Optional.of(finished(Decision.BLOCKED, NOW)), Optional.of(finished(Decision.REJECT, NOW)),
                Optional.of(InquiryRun.started(UUID.randomUUID(), REQUEST, NOW)),
                Optional.of(new InquiryRun(UUID.randomUUID(), REQUEST, RunStatus.STALE, Decision.PROCEED, NOW, NOW)),
                Optional.empty());

        assertThat(service.cleared(REQUEST)).isTrue();
        assertThat(service.cleared(REQUEST)).isFalse(); // blocked
        assertThat(service.cleared(REQUEST)).isFalse(); // rejected
        assertThat(service.cleared(REQUEST)).isFalse(); // still running
        assertThat(service.cleared(REQUEST)).isFalse(); // stale
        assertThat(service.cleared(REQUEST)).isFalse(); // never run
    }

    @Test
    void anUnknownRunOrAnotherRequestsRunIsNotFound() {
        UUID id = UUID.randomUUID();
        when(runs.find(REQUEST, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(REQUEST, id)).isInstanceOf(InquiryRunNotFoundException.class);
    }
}
