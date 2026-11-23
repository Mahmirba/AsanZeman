package com.sb24.asanzeman.inquiry.application;

import com.sb24.asanzeman.inquiry.domain.model.FailureKind;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryResult;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.domain.port.out.InquiryHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the inquiries of one stage in parallel, each on its own virtual thread (D24, D25, 5.1). The first
 * {@code NEGATIVE} cancels the rest, and the stage deadline cancels any that are still running. Leaving the scope
 * guarantees that no inquiry of the stage is still running.
 *
 * <p>This is the only class that uses the preview structured-concurrency API: a Java upgrade must re-check it (D25).
 */
@Component
public class StageRunner {

    private static final Logger log = LoggerFactory.getLogger(StageRunner.class);

    /**
     * @return one outcome per handler, in the order of {@code handlers}. A handler that did not answer is
     *         {@code FAILED}: {@code TIMEOUT} when the deadline passed, {@code CANCELLED} when a rejection stopped it
     */
    public List<InquiryOutcome> run(List<InquiryHandler> handlers, InquiryContext context, Duration deadline) {
        if (handlers.isEmpty()) {
            return List.of();
        }
        var answers = new ConcurrentHashMap<InquiryType, InquiryOutcome>();
        boolean timedOut = false;
        try (var scope = StructuredTaskScope.open(new StopOnFirstNegative(answers),
                config -> config.withTimeout(deadline))) {
            handlers.forEach(handler -> scope.fork(() -> ask(handler, context)));
            scope.join();
        } catch (StructuredTaskScope.TimeoutException e) {
            timedOut = true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        FailureKind missing = timedOut ? FailureKind.TIMEOUT : FailureKind.CANCELLED;
        return handlers.stream().map(h -> answers.getOrDefault(h.type(), InquiryOutcome.failed(h.type(), missing)))
                .toList();
    }

    /** A handler answers with an outcome; one that throws anyway is a failed inquiry, never a lost run. */
    private static InquiryOutcome ask(InquiryHandler handler, InquiryContext context) {
        try {
            return handler.execute(context);
        } catch (RuntimeException e) {
            log.warn("inquiry {} threw instead of answering", handler.type(), e);
            return InquiryOutcome.failed(handler.type(), FailureKind.UNAVAILABLE);
        }
    }

    /** Stops the stage at the first {@code NEGATIVE}; every answer is kept as it arrives. */
    private static final class StopOnFirstNegative
            implements StructuredTaskScope.Joiner<InquiryOutcome, Map<InquiryType, InquiryOutcome>> {

        private final Map<InquiryType, InquiryOutcome> answers;

        StopOnFirstNegative(Map<InquiryType, InquiryOutcome> answers) {
            this.answers = answers;
        }

        private volatile boolean rejected;

        @Override
        public boolean onComplete(Subtask<InquiryOutcome> subtask) {
            if (rejected) {
                return true; // the stage is decided: an answer that arrives while it is being cancelled does not count
            }
            InquiryOutcome outcome = subtask.get();
            answers.put(outcome.type(), outcome);
            rejected = outcome.result() == InquiryResult.NEGATIVE;
            return rejected;
        }

        @Override
        public Map<InquiryType, InquiryOutcome> result() {
            return Map.copyOf(answers);
        }
    }
}
