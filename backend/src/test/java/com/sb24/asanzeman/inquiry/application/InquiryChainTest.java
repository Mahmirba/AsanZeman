package com.sb24.asanzeman.inquiry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.inquiry.domain.model.CivilData;
import com.sb24.asanzeman.inquiry.domain.model.FailureKind;
import com.sb24.asanzeman.inquiry.domain.model.Gender;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryResult;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict.Decision;
import com.sb24.asanzeman.inquiry.domain.port.out.InquiryHandler;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

import static com.sb24.asanzeman.inquiry.domain.model.InquiryType.BLACKLIST;
import static com.sb24.asanzeman.inquiry.domain.model.InquiryType.SABT_AHVAL;
import static com.sb24.asanzeman.inquiry.domain.model.InquiryType.SAMAT;
import static com.sb24.asanzeman.inquiry.domain.model.InquiryType.SEPAD;
import static com.sb24.asanzeman.inquiry.domain.model.InquiryType.SHAHKAR;

/** The two stages of D26 with the real StageRunner and fake inquiries. */
class InquiryChainTest {

    private static final InquiryContext START = new InquiryContext("1000000011", "09121234567", 1000, "0123");
    private static final CivilData MAN = new CivilData(Gender.MALE, LocalDate.of(1990, 5, 17));
    private static final InquirySettings SETTINGS = new InquirySettings(
            List.of(List.of(SABT_AHVAL), List.of(SHAHKAR, SEPAD, BLACKLIST, SAMAT)),
            List.of(Duration.ofSeconds(10), Duration.ofSeconds(10)));

    private final List<InquiryType> asked = new CopyOnWriteArrayList<>();

    /** Counts what was asked, so a test can say which stage never ran. */
    private InquiryHandler recording(InquiryHandler inner) {
        return new InquiryHandler() {
            @Override
            public InquiryType type() {
                return inner.type();
            }

            @Override
            public boolean appliesTo(InquiryContext context) {
                return inner.appliesTo(context);
            }

            @Override
            public InquiryOutcome execute(InquiryContext context) {
                asked.add(inner.type());
                return inner.execute(context);
            }
        };
    }

    private InquiryChain chain(InquiryOutcome sabt, InquiryHandler... stage2) {
        var handlers = new ArrayList<InquiryHandler>();
        handlers.add(recording(FakeHandler.answering(SABT_AHVAL, sabt)));
        for (InquiryHandler h : stage2) {
            handlers.add(recording(h));
        }
        return new InquiryChain(handlers, new StageRunner(), SETTINGS);
    }

    private static InquiryHandler pass(InquiryType type) {
        return FakeHandler.answering(type, InquiryOutcome.passed(type));
    }

    private static InquiryOutcome sabtPassed() {
        return InquiryOutcome.passed(SABT_AHVAL, MAN);
    }

    private static List<InquiryResult> results(InquiryVerdict verdict, InquiryType... types) {
        return java.util.Arrays.stream(types).map(t -> verdict.outcomes().stream()
                .filter(o -> o.type() == t).findFirst().orElseThrow().result()).toList();
    }

    @Test
    void everythingPassedMeansOnToTheCollateral() {
        var verdict = chain(sabtPassed(), pass(SHAHKAR), pass(SEPAD), pass(BLACKLIST), pass(SAMAT)).run(START);

        assertThat(verdict.decision()).isEqualTo(Decision.PROCEED);
        assertThat(verdict.rejection()).isNull();
        assertThat(verdict.outcomes()).hasSize(5);
        assertThat(asked).hasSize(5);
    }

    @Test
    void sabteAhvalsGenderAndBirthDateReachTheSecondStage() {
        var seen = new CopyOnWriteArrayList<CivilData>();
        var spy = FakeHandler.answering(SHAHKAR, context -> {
            seen.add(context.civil());
            return InquiryOutcome.passed(SHAHKAR);
        });

        chain(sabtPassed(), spy, pass(SEPAD), pass(BLACKLIST), pass(SAMAT)).run(START);

        assertThat(seen).containsExactly(MAN);
    }

    @Test
    void aNegativeInStageOneRejectsAndStageTwoNeverRuns() {
        var verdict = chain(InquiryOutcome.negative(SABT_AHVAL, "inquiry.sabt-ahval.deceased"), pass(SHAHKAR),
                pass(SEPAD), pass(BLACKLIST), pass(SAMAT)).run(START);

        assertThat(verdict.decision()).isEqualTo(Decision.REJECT);
        assertThat(verdict.rejection().messageKey()).isEqualTo("inquiry.sabt-ahval.deceased");
        assertThat(asked).containsExactly(SABT_AHVAL);
    }

    @Test
    void aFailureInStageOneBlocksAndStageTwoNeverRuns() {
        var verdict = chain(InquiryOutcome.failed(SABT_AHVAL, FailureKind.TIMEOUT), pass(SHAHKAR), pass(SEPAD),
                pass(BLACKLIST), pass(SAMAT)).run(START);

        assertThat(verdict.decision()).isEqualTo(Decision.BLOCKED);
        assertThat(verdict.rejection()).isNull();
        assertThat(asked).containsExactly(SABT_AHVAL);
    }

    @Test
    void sabteAhvalThatPassesWithoutCivilDataIsNoUsableAnswer() {
        var verdict = chain(InquiryOutcome.passed(SABT_AHVAL), pass(SHAHKAR), pass(SEPAD), pass(BLACKLIST),
                pass(SAMAT)).run(START);

        assertThat(verdict.decision()).isEqualTo(Decision.BLOCKED);
        assertThat(verdict.outcomes()).containsExactly(InquiryOutcome.failed(SABT_AHVAL, FailureKind.BAD_RESPONSE));
        assertThat(asked).containsExactly(SABT_AHVAL);
    }

    @Test
    void aNegativeInStageTwoRejectsWithThatMessage() {
        var verdict = chain(sabtPassed(), pass(SHAHKAR), pass(SEPAD),
                FakeHandler.answering(BLACKLIST, InquiryOutcome.negative(BLACKLIST, "inquiry.blacklist.negative")),
                pass(SAMAT)).run(START);

        assertThat(verdict.decision()).isEqualTo(Decision.REJECT);
        assertThat(verdict.rejection().type()).isEqualTo(BLACKLIST);
        assertThat(verdict.rejection().messageKey()).isEqualTo("inquiry.blacklist.negative");
    }

    @Test
    void aNegativeBeatsAFailureInTheSameStage() {
        var verdict = chain(sabtPassed(),
                FakeHandler.answering(SHAHKAR, InquiryOutcome.failed(SHAHKAR, FailureKind.UNAVAILABLE)), pass(SEPAD),
                FakeHandler.answering(BLACKLIST, InquiryOutcome.negative(BLACKLIST, "m")), pass(SAMAT)).run(START);

        // the failed one may or may not have answered before the negative cancelled the rest
        assertThat(verdict.decision()).isEqualTo(Decision.REJECT);
    }

    @Test
    void aFailureInStageTwoBlocksWithNothingRejected() {
        var verdict = chain(sabtPassed(), pass(SHAHKAR), pass(SEPAD), pass(BLACKLIST),
                FakeHandler.answering(SAMAT, InquiryOutcome.failed(SAMAT, FailureKind.CIRCUIT_OPEN))).run(START);

        assertThat(verdict.decision()).isEqualTo(Decision.BLOCKED);
        assertThat(verdict.rejection()).isNull();
        assertThat(results(verdict, SAMAT)).containsExactly(InquiryResult.FAILED);
    }

    @Test
    void anInquiryThatDoesNotApplyIsSkippedAndNeverAsked() {
        var sepad = FakeHandler.applyingWhen(SEPAD, c -> c.civil().gender() == Gender.MALE,
                InquiryOutcome.passed(SEPAD));
        var woman = new CivilData(Gender.FEMALE, LocalDate.of(1990, 5, 17));

        var verdict = chain(InquiryOutcome.passed(SABT_AHVAL, woman), pass(SHAHKAR), sepad, pass(BLACKLIST),
                pass(SAMAT)).run(START);

        assertThat(verdict.decision()).isEqualTo(Decision.PROCEED);
        assertThat(results(verdict, SEPAD)).containsExactly(InquiryResult.SKIPPED);
        assertThat(asked).doesNotContain(SEPAD);
    }

    @Test
    void aConfiguredInquiryWithoutAHandlerStopsTheStartup() {
        assertThatThrownBy(() -> new InquiryChain(List.of(pass(SABT_AHVAL)), new StageRunner(), SETTINGS))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SHAHKAR");
    }

    @Test
    void everyStageNeedsExactlyOneDeadline() {
        assertThatThrownBy(() -> new InquirySettings(List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InquirySettings(List.of(List.of(SABT_AHVAL)), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InquirySettings(List.of(List.of(SABT_AHVAL)),
                List.of(Duration.ofSeconds(1), Duration.ofSeconds(2)))).isInstanceOf(IllegalArgumentException.class);
    }
}
