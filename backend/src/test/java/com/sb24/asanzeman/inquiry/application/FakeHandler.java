package com.sb24.asanzeman.inquiry.application;

import com.sb24.asanzeman.inquiry.domain.model.FailureKind;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.domain.port.out.InquiryHandler;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

/** An inquiry handler for the engine tests: what it answers, whether it applies, and how it behaves in time. */
final class FakeHandler implements InquiryHandler {

    private final InquiryType type;
    private final Predicate<InquiryContext> applies;
    private final Function<InquiryContext, InquiryOutcome> execute;

    private FakeHandler(InquiryType type, Predicate<InquiryContext> applies,
            Function<InquiryContext, InquiryOutcome> execute) {
        this.type = type;
        this.applies = applies;
        this.execute = execute;
    }

    static FakeHandler answering(InquiryType type, InquiryOutcome outcome) {
        return new FakeHandler(type, c -> true, c -> outcome);
    }

    static FakeHandler answering(InquiryType type, Function<InquiryContext, InquiryOutcome> execute) {
        return new FakeHandler(type, c -> true, execute);
    }

    static FakeHandler applyingWhen(InquiryType type, Predicate<InquiryContext> applies, InquiryOutcome outcome) {
        return new FakeHandler(type, applies, c -> outcome);
    }

    static FakeHandler throwing(InquiryType type) {
        return new FakeHandler(type, c -> true, c -> {
            throw new IllegalStateException("the client broke");
        });
    }

    /** Waits for the others to start too, so only a parallel run can complete. */
    static FakeHandler waitingFor(InquiryType type, CountDownLatch startedTogether) {
        return new FakeHandler(type, c -> true, c -> {
            startedTogether.countDown();
            try {
                if (!startedTogether.await(5, TimeUnit.SECONDS)) {
                    return InquiryOutcome.failed(type, FailureKind.TIMEOUT);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return InquiryOutcome.passed(type);
        });
    }

    /** Answers only once {@code running} says the slow inquiry is under way, so the answer finds it to cancel. */
    static FakeHandler answeringWhenRunning(InquiryType type, InquiryOutcome outcome, CountDownLatch running) {
        return new FakeHandler(type, c -> true, c -> {
            try {
                running.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return outcome;
        });
    }

    /** Sleeps until it is interrupted, and records that it was. */
    static FakeHandler neverAnswering(InquiryType type, AtomicBoolean interrupted) {
        return neverAnswering(type, interrupted, new CountDownLatch(1));
    }

    /** Same, and says when it has started. */
    static FakeHandler neverAnswering(InquiryType type, AtomicBoolean interrupted, CountDownLatch started) {
        return new FakeHandler(type, c -> true, c -> {
            started.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
            }
            return InquiryOutcome.passed(type);
        });
    }

    @Override
    public InquiryType type() {
        return type;
    }

    @Override
    public boolean appliesTo(InquiryContext context) {
        return applies.test(context);
    }

    @Override
    public InquiryOutcome execute(InquiryContext context) {
        return execute.apply(context);
    }
}
