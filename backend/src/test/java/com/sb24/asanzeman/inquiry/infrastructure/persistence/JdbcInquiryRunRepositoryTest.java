package com.sb24.asanzeman.inquiry.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.inquiry.domain.model.CivilData;
import com.sb24.asanzeman.inquiry.domain.model.FailureKind;
import com.sb24.asanzeman.inquiry.domain.model.Gender;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRun;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict.Decision;
import com.sb24.asanzeman.inquiry.domain.model.RunStatus;
import com.sb24.asanzeman.support.H2Auth;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Real SQL on in-memory H2. */
class JdbcInquiryRunRepositoryTest {

    private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");
    private static final CivilData MAN = new CivilData(Gender.MALE, LocalDate.of(1990, 5, 17));

    private final JdbcInquiryRunRepository repository = new JdbcInquiryRunRepository(H2Auth.newDatabase());
    private final UUID request = UUID.randomUUID();

    private InquiryRun start(UUID requestId, Instant at) {
        var run = InquiryRun.started(UUID.randomUUID(), requestId, at);
        assertThat(repository.insertRunning(run)).isTrue();
        return run;
    }

    @Test
    void aRunIsRecordedAsRunningAndFound() {
        var run = start(request, T0);

        assertThat(repository.findRunning(request)).contains(run);
        assertThat(repository.find(request, run.id())).contains(run);
        assertThat(repository.findLatest(request)).contains(run);
    }

    @Test
    void aRequestHasOnlyOneRunningRun() {
        start(request, T0);

        assertThat(repository.insertRunning(InquiryRun.started(UUID.randomUUID(), request, T0.plusSeconds(1))))
                .isFalse();
        assertThat(repository.insertRunning(InquiryRun.started(UUID.randomUUID(), UUID.randomUUID(), T0))).isTrue();
    }

    @Test
    void aRunIsOnlyFoundForItsOwnRequest() {
        var run = start(request, T0);

        assertThat(repository.find(UUID.randomUUID(), run.id())).isEmpty();
        assertThat(repository.find(request, UUID.randomUUID())).isEmpty();
        assertThat(repository.findRunning(UUID.randomUUID())).isEmpty();
        assertThat(repository.findLatest(UUID.randomUUID())).isEmpty();
    }

    @Test
    void finishingSavesTheDecisionAndEveryResultAndFreesTheRequestForANewRun() {
        var run = start(request, T0);
        var outcomes = List.of(InquiryOutcome.passed(InquiryType.SABT_AHVAL, MAN),
                InquiryOutcome.negative(InquiryType.BLACKLIST, "inquiry.blacklist.negative"),
                InquiryOutcome.failed(InquiryType.SAMAT, FailureKind.TIMEOUT),
                InquiryOutcome.skipped(InquiryType.SEPAD));

        assertThat(repository.finish(run.id(), Decision.REJECT, outcomes, T0.plusSeconds(8))).isTrue();

        var saved = repository.find(request, run.id()).orElseThrow();
        assertThat(saved.status()).isEqualTo(RunStatus.FINISHED);
        assertThat(saved.decision()).isEqualTo(Decision.REJECT);
        assertThat(saved.finishedAt()).isEqualTo(T0.plusSeconds(8));
        assertThat(repository.results(run.id())).containsExactlyInAnyOrderElementsOf(outcomes);
        assertThat(repository.findRunning(request)).isEmpty();
        assertThat(repository.insertRunning(InquiryRun.started(UUID.randomUUID(), request, T0.plusSeconds(9))))
                .isTrue();
    }

    @Test
    void aRunThatIsNoLongerRunningIsNotFinishedAgain() {
        var run = start(request, T0);
        repository.finish(run.id(), Decision.PROCEED, List.of(InquiryOutcome.passed(InquiryType.SHAHKAR)),
                T0.plusSeconds(1));

        assertThat(repository.finish(run.id(), Decision.BLOCKED, List.of(), T0.plusSeconds(2))).isFalse();
        assertThat(repository.results(run.id())).hasSize(1);
        assertThat(repository.find(request, run.id()).orElseThrow().decision()).isEqualTo(Decision.PROCEED);
    }

    @Test
    void aRunThatNeverFinishedBecomesStaleAndFreesTheRequest() {
        var dead = start(request, T0);

        repository.markStale(request, T0.plusSeconds(60));

        assertThat(repository.find(request, dead.id()).orElseThrow().status()).isEqualTo(RunStatus.STALE);
        assertThat(repository.findRunning(request)).isEmpty();
        assertThat(repository.insertRunning(InquiryRun.started(UUID.randomUUID(), request, T0.plusSeconds(61))))
                .isTrue();
        assertThat(repository.finish(dead.id(), Decision.PROCEED, List.of(), T0.plusSeconds(62))).isFalse();
    }

    @Test
    void aRunStillInsideTheLimitIsNotMadeStale() {
        var live = start(request, T0);

        repository.markStale(request, T0); // began exactly at the limit: not before it

        assertThat(repository.findRunning(request)).contains(live);
    }

    @Test
    void theLatestRunIsTheNewestStarted() {
        var first = start(request, T0);
        repository.finish(first.id(), Decision.BLOCKED, List.of(), T0.plusSeconds(5));
        var second = start(request, T0.plusSeconds(100));

        assertThat(repository.findLatest(request)).contains(second);
    }

    @Test
    void passedResultsInsideTheWindowAreReusableAndTheNewestOfEachWins() {
        var first = start(request, T0);
        repository.finish(first.id(), Decision.BLOCKED, List.of(InquiryOutcome.passed(InquiryType.SABT_AHVAL, MAN),
                InquiryOutcome.passed(InquiryType.SHAHKAR), InquiryOutcome.failed(InquiryType.SAMAT,
                        FailureKind.UNAVAILABLE), InquiryOutcome.skipped(InquiryType.SEPAD)), T0.plusSeconds(5));
        var second = start(request, T0.plusSeconds(100));
        var womanCivil = new CivilData(Gender.FEMALE, LocalDate.of(1995, 1, 1));
        repository.finish(second.id(), Decision.BLOCKED, List.of(InquiryOutcome.passed(InquiryType.SABT_AHVAL,
                womanCivil), InquiryOutcome.passed(InquiryType.SAMAT)), T0.plusSeconds(105));

        var reusable = repository.passedSince(request, T0.plusSeconds(105).minus(Duration.ofHours(1)));

        assertThat(reusable).containsOnlyKeys(InquiryType.SABT_AHVAL, InquiryType.SHAHKAR, InquiryType.SAMAT);
        assertThat(reusable.get(InquiryType.SABT_AHVAL).civil()).isEqualTo(womanCivil);
    }

    @Test
    void aPassedResultOlderThanTheWindowIsNotReusedNorAnotherRequests() {
        var old = start(request, T0);
        repository.finish(old.id(), Decision.BLOCKED, List.of(InquiryOutcome.passed(InquiryType.SHAHKAR)),
                T0.plusSeconds(5));
        var other = start(UUID.randomUUID(), T0);
        repository.finish(other.id(), Decision.BLOCKED, List.of(InquiryOutcome.passed(InquiryType.SAMAT)),
                T0.plusSeconds(5));

        assertThat(repository.passedSince(request, T0.plusSeconds(6))).isEmpty();
        assertThat(repository.passedSince(request, T0)).containsOnlyKeys(InquiryType.SHAHKAR);
    }
}
