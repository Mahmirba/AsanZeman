package com.sb24.asanzeman.inquiry.domain.port.in;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict;
import java.util.Map;

/** Runs the two-stage inquiry chain for one customer and request (5.1). */
public interface RunInquiriesUseCase {

    /**
     * Blocks until the run is decided; it never throws for a service fault. An inquiry in {@code reusable} (it passed
     * lately) is not asked again: its saved outcome stands in for it.
     */
    InquiryVerdict run(InquiryContext context, Map<InquiryType, InquiryOutcome> reusable);

    default InquiryVerdict run(InquiryContext context) {
        return run(context, Map.of());
    }
}
