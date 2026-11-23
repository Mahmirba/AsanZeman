package com.sb24.asanzeman.inquiry.infrastructure.external;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.integration.inquiry.InquiryClient;
import com.sb24.asanzeman.integration.inquiry.InquiryEndpoint;
import com.sb24.asanzeman.integration.inquiry.InquiryReply;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Samat, non-current loans at every bank (5.3 #6). The customer passes when, over all loans, the overdue
 * ({@code amSarResid}), deferred ({@code amMoavagh}) and doubtful ({@code amMashkuk}) amounts add up to zero, also
 * when there are no loans; any amount rejects. {@code hasError} and a missing {@code returnValue} are no answer.
 * Amounts are decimal numbers, so they are added as {@link BigDecimal}.
 */
public class SamatHandler extends JsonInquiryHandler {

    public SamatHandler(InquiryClient client) {
        super(InquiryType.SAMAT, client);
    }

    @Override
    InquiryReply ask(InquiryContext context) {
        InquiryEndpoint e = client.endpoint();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("estlmId", e.field("inquiry-id", "1"));
        body.put("cstmrType", e.field("customer-type", "1"));
        body.put("cstmrShenase", context.nationalCode());
        return client.post(body, Map.of());
    }

    @Override
    InquiryOutcome decide(InquiryContext context, InquiryReply reply) {
        requireOk(reply);
        JsonNode body = json(reply);
        JsonNode value = body.path("returnValue");
        if (body.path("hasError").asBoolean(false) || !value.isObject()) {
            throw new IllegalStateException("the service reports " + body.path("errors"));
        }
        JsonNode rows = value.path("estelamAsliRows");
        if (rows.isMissingNode()) {
            throw new IllegalStateException("no list of loans in the answer");
        }
        BigDecimal nonCurrent = BigDecimal.ZERO;
        for (JsonNode row : rows) { // a null list means no loans
            nonCurrent = nonCurrent.add(amount(row, "amSarResid")).add(amount(row, "amMoavagh"))
                    .add(amount(row, "amMashkuk"));
        }
        return nonCurrent.signum() == 0 ? InquiryOutcome.passed(type())
                : InquiryOutcome.negative(type(), Messages.NON_CURRENT_LOAN);
    }

    /** A missing amount is not zero: a loan row that cannot be read means no usable answer. */
    private static BigDecimal amount(JsonNode row, String field) {
        JsonNode node = row.path(field);
        if (!node.isNumber()) {
            throw new IllegalStateException("loan row without " + field);
        }
        return node.decimalValue();
    }
}
