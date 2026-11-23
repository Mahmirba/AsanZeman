package com.sb24.asanzeman.inquiry.infrastructure.external;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.integration.inquiry.InquiryClient;
import com.sb24.asanzeman.integration.inquiry.InquiryReply;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * The central bank's blacklist (5.3 #4). The answer is inverted: {@code result: true} means the customer is on the
 * list and is rejected; {@code false} passes. A missing or non-boolean {@code result} is no answer.
 */
public class BlacklistHandler extends JsonInquiryHandler {

    public BlacklistHandler(InquiryClient client) {
        super(InquiryType.BLACKLIST, client);
    }

    @Override
    InquiryReply ask(InquiryContext context) {
        return client.post(Map.of("nationalKey", context.nationalCode()), Map.of());
    }

    @Override
    InquiryOutcome decide(InquiryContext context, InquiryReply reply) {
        requireOk(reply);
        JsonNode result = json(reply).path("result");
        if (!result.isBoolean()) {
            throw new IllegalStateException("no result in the answer");
        }
        boolean onBlacklist = result.asBoolean();
        return onBlacklist ? InquiryOutcome.negative(type(), Messages.CENTRAL_BANK) : InquiryOutcome.passed(type());
    }
}
