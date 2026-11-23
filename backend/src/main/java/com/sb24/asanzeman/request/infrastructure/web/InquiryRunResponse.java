package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryResult;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRunView;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict;
import java.util.List;
import java.util.UUID;
import org.springframework.context.MessageSource;

/**
 * A run as the customer panel polls it (5.1). {@code status} is {@code RUNNING}, {@code FINISHED} or {@code STALE};
 * {@code decision} is {@code PROCEED}, {@code BLOCKED} or {@code REJECT} once it has finished. {@code message} is what
 * to show the customer: the SRS rejection text, or the "try again in a few minutes" text when it is blocked.
 */
public record InquiryRunResponse(UUID runId, String status, String decision, String message,
        List<Item> inquiries) {

    /** One inquiry of the run: its result, and the kind of failure when there was no usable answer. */
    public record Item(String type, String result, String failure) {
    }

    static InquiryRunResponse from(InquiryRunView view, MessageSource messages) {
        var run = view.run();
        return new InquiryRunResponse(run.id(), run.status().name(),
                run.decision() == null ? null : run.decision().name(), message(view, messages),
                view.outcomes().stream().map(o -> new Item(o.type().name(), o.result().name(),
                        o.failure() == null ? null : o.failure().name())).toList());
    }

    private static String message(InquiryRunView view, MessageSource messages) {
        InquiryVerdict.Decision decision = view.run().decision();
        if (decision == InquiryVerdict.Decision.BLOCKED) {
            return messages.getMessage("inquiry.blocked", null, RequestSummaryResponse.FARSI);
        }
        if (decision != InquiryVerdict.Decision.REJECT) {
            return null;
        }
        return view.outcomes().stream().filter(o -> o.result() == InquiryResult.NEGATIVE).findFirst()
                .map(InquiryOutcome::messageKey)
                .map(key -> messages.getMessage(key, null, key, RequestSummaryResponse.FARSI)).orElse(null);
    }
}
