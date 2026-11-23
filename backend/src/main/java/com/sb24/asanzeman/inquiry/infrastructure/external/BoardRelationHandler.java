package com.sb24.asanzeman.inquiry.infrastructure.external;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.integration.inquiry.InquiryClient;
import com.sb24.asanzeman.integration.inquiry.InquiryReply;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Board relation, the central bank's credit limits (5.3 #7): is the guarantee amount inside the customer's limits?
 * {@code result: true} passes and {@code false} rejects. The {@code description} says which limit was broken; it stays
 * in the raw answer for the branch (D22). A missing {@code result} is no answer.
 */
public class BoardRelationHandler extends JsonInquiryHandler {

    public BoardRelationHandler(InquiryClient client) {
        super(InquiryType.BOARD_RELATION, client);
    }

    @Override
    InquiryReply ask(InquiryContext context) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", context.amount());
        body.put("customerType", Integer.parseInt(client.endpoint().field("customer-type", "1")));
        body.put("nationalIdOrLegalId", context.nationalCode());
        body.put("birthDateOrRegisterDate", JalaliDate.fromGregorian(context.civil().birthDate()).toCompact());
        return client.post(body, Map.of());
    }

    @Override
    InquiryOutcome decide(InquiryContext context, InquiryReply reply) {
        requireOk(reply);
        JsonNode result = json(reply).path("result");
        if (!result.isBoolean()) {
            throw new IllegalStateException("no result in the answer");
        }
        return result.asBoolean() ? InquiryOutcome.passed(type())
                : InquiryOutcome.negative(type(), Messages.CENTRAL_BANK);
    }
}
