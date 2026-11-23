package com.sb24.asanzeman.inquiry.infrastructure.external;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.integration.inquiry.InquiryClient;
import com.sb24.asanzeman.integration.inquiry.InquiryEndpoint;
import com.sb24.asanzeman.integration.inquiry.InquiryReply;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Sama Cheque, bounced cheques (5.3 #5). {@code isValid: true} with no cheques passes; any cheque rejects. {@code
 * isValid: false} (an error such as {@code chq402} for a wrong national code) and a missing {@code cheques} list are no
 * answer. The cheque details are kept in the raw answer for the branch (D22).
 */
public class SamaChequeHandler extends JsonInquiryHandler {

    public SamaChequeHandler(InquiryClient client) {
        super(InquiryType.SAMA_CHEQUE, client);
    }

    @Override
    InquiryReply ask(InquiryContext context) {
        InquiryEndpoint e = client.endpoint();
        Map<String, Object> person = new LinkedHashMap<>();
        person.put("nationalId", context.nationalCode());
        person.put("personType", Integer.parseInt(e.field("person-type", "1")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("personInfo", person);
        body.put("requestInfo", e.field("request-info", "1"));
        return client.post(body, Map.of());
    }

    @Override
    InquiryOutcome decide(InquiryContext context, InquiryReply reply) {
        requireOk(reply);
        JsonNode body = json(reply);
        if (!body.path("isValid").asBoolean(false)) {
            throw new IllegalStateException("the service reports " + body.path("errors"));
        }
        JsonNode cheques = body.path("cheques");
        if (!cheques.isArray()) {
            throw new IllegalStateException("no list of cheques in the answer");
        }
        return cheques.isEmpty() ? InquiryOutcome.passed(type())
                : InquiryOutcome.negative(type(), Messages.BOUNCED_CHEQUE);
    }
}
