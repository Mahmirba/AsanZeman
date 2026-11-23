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
 * Shahkar (5.3 #2): does the mobile number belong to this national code? The service always says {@code id: "200"}, so
 * the answer is the {@code response} field: {@code 200} passes, {@code 600} (NotIdentifiedException) is a mismatch,
 * and anything else, such as {@code 311} for a national code the service finds invalid, is no usable answer.
 */
public class ShahkarHandler extends JsonInquiryHandler {

    public static final String MISMATCH = "inquiry.shahkar.mismatch";

    public ShahkarHandler(InquiryClient client) {
        super(InquiryType.SHAHKAR, client);
    }

    @Override
    InquiryReply ask(InquiryContext context) {
        InquiryEndpoint e = client.endpoint();
        // this service spells its tracking block in PascalCase and wants the type fields as text
        Map<String, Object> tracking = new LinkedHashMap<>();
        tracking.put("UserName", e.field("user-name"));
        tracking.put("Ip", e.field("ip"));
        tracking.put("BranchCode", Fields.branch(e, context));
        tracking.put("ChanelCode", Integer.parseInt(e.field("channel-code")));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("serviceNumber", context.mobile());
        body.put("identificationNo", context.nationalCode());
        body.put("identificationType", e.field("identification-type", "0"));
        body.put("serviceType", e.field("service-type", "2"));
        body.put("ClientTrackingparam", tracking);
        return client.post(body, Map.of());
    }

    @Override
    InquiryOutcome decide(InquiryContext context, InquiryReply reply) {
        requireOk(reply);
        JsonNode body = json(reply);
        return switch (body.path("response").asInt(-1)) {
            case 200 -> InquiryOutcome.passed(type());
            case 600 -> InquiryOutcome.negative(type(), MISMATCH);
            default -> throw new IllegalStateException("shahkar answered " + body.path("response"));
        };
    }
}
