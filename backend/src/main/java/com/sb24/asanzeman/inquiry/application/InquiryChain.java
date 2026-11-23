package com.sb24.asanzeman.inquiry.application;

import com.sb24.asanzeman.inquiry.domain.model.FailureKind;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryResult;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict;
import com.sb24.asanzeman.inquiry.domain.port.in.RunInquiriesUseCase;
import com.sb24.asanzeman.inquiry.domain.port.out.InquiryHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The chain of stages (D26, 5.1). A stage that rejects or blocks ends the run, so stage 2 never runs when Sabte
 * Ahval fails. Sabte Ahval gender and birth date go into the context for the stages after it.
 *
 * <p>Not a Spring bean yet: it refuses to start without a handler for every configured inquiry, so it is registered
 * once the inquiry clients exist.
 */
public class InquiryChain implements RunInquiriesUseCase {

    private final Map<InquiryType, InquiryHandler> handlers;
    private final StageRunner stages;
    private final InquirySettings settings;

    public InquiryChain(List<InquiryHandler> handlers, StageRunner stages, InquirySettings settings) {
        this.handlers = handlers.stream().collect(Collectors.toMap(InquiryHandler::type, Function.identity()));
        this.stages = stages;
        this.settings = settings;
        settings.stages().stream().flatMap(List::stream).filter(type -> !this.handlers.containsKey(type))
                .findFirst().ifPresent(type -> {
                    throw new IllegalStateException("no handler for the configured inquiry " + type);
                });
    }

    @Override
    public InquiryVerdict run(InquiryContext start) {
        InquiryContext context = start;
        List<InquiryOutcome> all = new ArrayList<>();
        for (int i = 0; i < settings.stages().size(); i++) {
            List<InquiryHandler> stage = settings.stages().get(i).stream().map(handlers::get).toList();
            InquiryContext current = context;
            // Sabte Ahval, which supplies the civil data, always applies; the others may depend on that data
            List<InquiryHandler> applicable = stage.stream()
                    .filter(h -> h.type() == InquiryType.SABT_AHVAL || h.appliesTo(current)).toList();
            stage.stream().filter(h -> !applicable.contains(h)).forEach(h -> all.add(InquiryOutcome.skipped(h.type())));

            List<InquiryOutcome> outcomes = stages.run(applicable, context, settings.deadlines().get(i)).stream()
                    .map(InquiryChain::civilDataRequired).toList();
            all.addAll(outcomes);

            InquiryOutcome negative = first(outcomes, InquiryResult.NEGATIVE);
            if (negative != null) {
                return new InquiryVerdict(InquiryVerdict.Decision.REJECT, negative, all);
            }
            if (first(outcomes, InquiryResult.FAILED) != null) {
                return new InquiryVerdict(InquiryVerdict.Decision.BLOCKED, null, all);
            }
            for (InquiryOutcome outcome : outcomes) {
                if (outcome.civil() != null) {
                    context = context.withCivil(outcome.civil());
                }
            }
        }
        return new InquiryVerdict(InquiryVerdict.Decision.PROCEED, null, all);
    }

    /** Sabte Ahval that passes without a gender and birth date cannot feed the next stage: no usable answer. */
    private static InquiryOutcome civilDataRequired(InquiryOutcome outcome) {
        boolean noData = outcome.type() == InquiryType.SABT_AHVAL && outcome.result() == InquiryResult.PASSED
                && outcome.civil() == null;
        return noData ? InquiryOutcome.failed(InquiryType.SABT_AHVAL, FailureKind.BAD_RESPONSE) : outcome;
    }

    private static InquiryOutcome first(List<InquiryOutcome> outcomes, InquiryResult result) {
        return outcomes.stream().filter(o -> o.result() == result).findFirst().orElse(null);
    }
}
