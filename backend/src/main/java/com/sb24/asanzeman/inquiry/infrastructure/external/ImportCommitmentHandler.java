package com.sb24.asanzeman.inquiry.infrastructure.external;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.integration.inquiry.InquiryClient;
import com.sb24.asanzeman.integration.inquiry.InquiryReply;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Import commitments, foreign-currency commitments not yet settled (5.3 #8). The answer is a list: every row
 * {@code izMojaz: true} passes, any {@code false} rejects. An empty or missing list, and HTTP 404, are no answer: unlike
 * the export inquiry, "nothing found" here does not mean "all clear". The channel code goes in a header.
 */
public class ImportCommitmentHandler extends JsonInquiryHandler {

    public ImportCommitmentHandler(InquiryClient client) {
        super(InquiryType.IMPORT_COMMITMENT, client);
    }

    @Override
    InquiryReply ask(InquiryContext context) {
        return client.post(Map.of("shenasehMelliOrShomarehKartBazargani", context.nationalCode()),
                Map.of("ChanelCode", client.endpoint().field("channel-code", "15")));
    }

    @Override
    InquiryOutcome decide(InquiryContext context, InquiryReply reply) {
        requireOk(reply);
        JsonNode rows = json(reply).path("returnData");
        if (!rows.isArray() || rows.isEmpty()) {
            throw new IllegalStateException("no commitments in the answer");
        }
        boolean allAllowed = true;
        for (JsonNode row : rows) {
            JsonNode allowed = row.path("izMojaz");
            if (!allowed.isBoolean()) {
                throw new IllegalStateException("a commitment without izMojaz");
            }
            allAllowed &= allowed.asBoolean();
        }
        return allAllowed ? InquiryOutcome.passed(type()) : InquiryOutcome.negative(type(), Messages.CENTRAL_BANK);
    }
}
