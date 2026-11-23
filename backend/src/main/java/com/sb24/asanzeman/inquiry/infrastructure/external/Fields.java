package com.sb24.asanzeman.inquiry.infrastructure.external;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.integration.inquiry.InquiryEndpoint;

/** Small helpers for the fixed request fields the inquiries share (5.3). */
final class Fields {

    private Fields() {
    }

    /** {@code branch-code: REQUEST} means "the branch of the request"; anything else is used as it is. */
    static String branch(InquiryEndpoint endpoint, InquiryContext context) {
        String configured = endpoint.field("branch-code");
        return "REQUEST".equals(configured) ? context.branchCode() : configured;
    }
}
