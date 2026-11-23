package com.sb24.asanzeman.inquiry.domain.port.out;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;

/** One inquiry as a strategy (D26): which one it is, whether it applies to this customer, and how to ask. */
public interface InquiryHandler {

    InquiryType type();

    /** Sepad, for example, only applies to men aged 18 to 50 (from Sabte Ahval's data in the context). */
    boolean appliesTo(InquiryContext context);

    /**
     * Asks the service. Retries and timeouts are the handler's own business; it answers with an outcome and does not
     * throw for a service fault. It is stopped by an interrupt when the run is cancelled.
     */
    InquiryOutcome execute(InquiryContext context);
}
