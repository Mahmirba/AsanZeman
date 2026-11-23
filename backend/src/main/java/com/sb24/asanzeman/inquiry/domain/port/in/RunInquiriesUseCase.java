package com.sb24.asanzeman.inquiry.domain.port.in;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict;

/** Runs the two-stage inquiry chain for one customer and request (5.1). */
public interface RunInquiriesUseCase {

    /** Blocks until the run is decided; it never throws for a service fault. */
    InquiryVerdict run(InquiryContext context);
}
