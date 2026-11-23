package com.sb24.asanzeman.inquiry.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.inquiry.domain.model.FailureKind;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryResult;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Real threads: the stage runs its inquiries side by side, stops at a negative, and ends at its deadline. */
class StageRunnerTest {

    private static final InquiryContext CONTEXT = new InquiryContext("1000000011", "09121234567", 1000, "0123");
    private static final Duration LONG = Duration.ofSeconds(10);

    private final StageRunner runner = new StageRunner();

    @Test
    void anEmptyStageHasNoOutcomes() {
        assertThat(runner.run(List.of(), CONTEXT, LONG)).isEmpty();
    }

    @Test
    void everyInquiryAnswersAndTheOutcomesFollowTheHandlerOrder() {
        var outcomes = runner.run(List.of(
                FakeHandler.answering(InquiryType.SHAHKAR, InquiryOutcome.passed(InquiryType.SHAHKAR)),
                FakeHandler.answering(InquiryType.BLACKLIST, InquiryOutcome.passed(InquiryType.BLACKLIST)),
                FakeHandler.answering(InquiryType.SAMAT, InquiryOutcome.failed(InquiryType.SAMAT,
                        FailureKind.UNAVAILABLE))), CONTEXT, LONG);

        assertThat(outcomes).extracting(InquiryOutcome::type)
                .containsExactly(InquiryType.SHAHKAR, InquiryType.BLACKLIST, InquiryType.SAMAT);
        assertThat(outcomes).extracting(InquiryOutcome::result)
                .containsExactly(InquiryResult.PASSED, InquiryResult.PASSED, InquiryResult.FAILED);
    }

    @Test
    void theInquiriesRunSideBySideNotOneAfterTheOther() {
        // each waits for the other to start: only parallel execution can finish
        var bothStarted = new CountDownLatch(2);
        var outcomes = runner.run(List.of(FakeHandler.waitingFor(InquiryType.SHAHKAR, bothStarted),
                FakeHandler.waitingFor(InquiryType.SAMAT, bothStarted)), CONTEXT, LONG);

        assertThat(outcomes).extracting(InquiryOutcome::result).containsOnly(InquiryResult.PASSED);
    }

    @Test
    void aNegativeStopsTheOthersAndTheyAreMarkedCancelled() {
        var stopped = new AtomicBoolean();
        var slowIsRunning = new CountDownLatch(1);
        var outcomes = runner.run(List.of(
                FakeHandler.answeringWhenRunning(InquiryType.BLACKLIST,
                        InquiryOutcome.negative(InquiryType.BLACKLIST, "m"), slowIsRunning),
                FakeHandler.neverAnswering(InquiryType.SAMAT, stopped, slowIsRunning)), CONTEXT, LONG);

        assertThat(outcomes.get(0).result()).isEqualTo(InquiryResult.NEGATIVE);
        assertThat(outcomes.get(1)).isEqualTo(InquiryOutcome.failed(InquiryType.SAMAT, FailureKind.CANCELLED));
        assertThat(stopped).isTrue(); // leaving the stage means the slow inquiry was interrupted, not left running
    }

    @Test
    void theDeadlineEndsTheStageAndTheSlowOnesAreTimedOut() {
        var stopped = new AtomicBoolean();
        long started = System.nanoTime();

        var outcomes = runner.run(List.of(
                FakeHandler.answering(InquiryType.SHAHKAR, InquiryOutcome.passed(InquiryType.SHAHKAR)),
                FakeHandler.neverAnswering(InquiryType.SAMAT, stopped)), CONTEXT, Duration.ofMillis(300));

        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(5000);
        assertThat(outcomes.get(0).result()).isEqualTo(InquiryResult.PASSED);
        assertThat(outcomes.get(1)).isEqualTo(InquiryOutcome.failed(InquiryType.SAMAT, FailureKind.TIMEOUT));
        assertThat(stopped).isTrue();
    }

    @Test
    void aHandlerThatThrowsIsAFailedInquiryAndTheOthersStillAnswer() {
        var outcomes = runner.run(List.of(FakeHandler.throwing(InquiryType.SAMAT),
                FakeHandler.answering(InquiryType.SHAHKAR, InquiryOutcome.passed(InquiryType.SHAHKAR))), CONTEXT, LONG);

        assertThat(outcomes.get(0)).isEqualTo(InquiryOutcome.failed(InquiryType.SAMAT, FailureKind.UNAVAILABLE));
        assertThat(outcomes.get(1).result()).isEqualTo(InquiryResult.PASSED);
    }

    @Test
    void aCallerThatIsInterruptedGetsFailedOutcomesAndKeepsItsInterruptFlag() {
        var stopped = new AtomicBoolean();
        Thread.currentThread().interrupt();
        try {
            var outcomes = runner.run(List.of(FakeHandler.neverAnswering(InquiryType.SAMAT, stopped)), CONTEXT, LONG);

            assertThat(outcomes.get(0)).isEqualTo(InquiryOutcome.failed(InquiryType.SAMAT, FailureKind.CANCELLED));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // never leak the flag into other tests
        }
    }
}
