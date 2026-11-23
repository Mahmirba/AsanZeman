package com.sb24.asanzeman.inquiry.infrastructure.external;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.integration.inquiry.InquiryClient;
import com.sb24.asanzeman.integration.inquiry.InquiryReply;
import tools.jackson.databind.JsonNode;

/**
 * Export commitments (5.3 #9), a GET with the national code in the path. {@code commitment_creation: true} passes and
 * {@code false} rejects. HTTP 404 with the detail «No MerchantCommitment matches the given query.» means the customer
 * is not an exporter, which also passes (the opposite of the import inquiry). A 404 with any other detail is a wrong
 * URL, which must never approve a customer: no answer.
 */
public class ExportCommitmentHandler extends JsonInquiryHandler {

    static final String NOT_AN_EXPORTER = "No MerchantCommitment matches the given query.";

    public ExportCommitmentHandler(InquiryClient client) {
        super(InquiryType.EXPORT_COMMITMENT, client);
    }

    @Override
    InquiryReply ask(InquiryContext context) {
        return client.get("/" + context.nationalCode());
    }

    @Override
    InquiryOutcome decide(InquiryContext context, InquiryReply reply) {
        if (reply.status() == 404) {
            if (NOT_AN_EXPORTER.equals(json(reply).path("detail").asString().trim())) {
                return InquiryOutcome.passed(type());
            }
            throw new IllegalStateException("404 with another detail");
        }
        requireOk(reply);
        JsonNode created = json(reply).path("commitment_creation");
        if (!created.isBoolean()) {
            throw new IllegalStateException("no commitment_creation in the answer");
        }
        return created.asBoolean() ? InquiryOutcome.passed(type())
                : InquiryOutcome.negative(type(), Messages.CENTRAL_BANK);
    }
}
