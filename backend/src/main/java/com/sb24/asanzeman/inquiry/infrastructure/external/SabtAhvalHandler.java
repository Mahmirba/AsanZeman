package com.sb24.asanzeman.inquiry.infrastructure.external;

import com.sb24.asanzeman.inquiry.domain.model.CivilData;
import com.sb24.asanzeman.inquiry.domain.model.Gender;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.integration.inquiry.InquiryClient;
import com.sb24.asanzeman.integration.inquiry.InquiryEndpoint;
import com.sb24.asanzeman.integration.inquiry.InquiryReply;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Sabte Ahval, the civil registry (5.3 #1, stage 1): checks the national code against the birth date saved with the
 * request, says whether the person is alive, and supplies the gender and birth date the later inquiries use (D26).
 *
 * <ul>
 *   <li>{@code statusField "200"} and {@code isaliveCode 0}: {@code PASSED}, with the civil data;</li>
 *   <li>{@code isaliveCode 1}: {@code NEGATIVE}, the person is deceased;</li>
 *   <li>{@code statusField "300"} ({@code err.record.not.found}): {@code NEGATIVE}, the national code and birth date
 *       do not belong together;</li>
 *   <li>everything else, including {@code hasError: true}, an unknown gender, or an unreadable date: {@code FAILED}.
 *       {@code hasError} alone is not trusted: a mismatch comes back with {@code hasError: false}.</li>
 * </ul>
 */
public class SabtAhvalHandler extends JsonInquiryHandler {

    public static final String DECEASED = "inquiry.sabt-ahval.deceased";
    public static final String MISMATCH = "inquiry.sabt-ahval.mismatch";

    public SabtAhvalHandler(InquiryClient client) {
        super(InquiryType.SABT_AHVAL, client);
    }

    @Override
    InquiryReply ask(InquiryContext context) {
        InquiryEndpoint e = client.endpoint();
        if (context.claimedBirthDate() == null) {
            throw new IllegalStateException("the request has no birth date to check");
        }
        String branch = Fields.branch(e, context);
        Map<String, Object> sabt = new LinkedHashMap<>();
        sabt.put("birthDate", JalaliDate.fromGregorian(context.claimedBirthDate()).toCompact());
        sabt.put("nationalCode", context.nationalCode());
        sabt.put("requestType", Integer.parseInt(e.field("request-type")));
        Map<String, Object> tracking = new LinkedHashMap<>();
        tracking.put("userName", e.field("user-name"));
        tracking.put("ip", e.field("ip"));
        tracking.put("branchCode", branch);
        tracking.put("chanelCode", Integer.parseInt(e.field("channel-code")));
        return client.post(Map.of("sabtParam", sabt, "clientTrackingParam", tracking), Map.of());
    }

    @Override
    InquiryOutcome decide(InquiryContext context, InquiryReply reply) {
        requireOk(reply);
        JsonNode body = json(reply);
        if (body.path("hasError").asBoolean(false)) {
            throw new IllegalStateException("the service reports an error");
        }
        String status = body.path("serviceStatus").path("statusField").asString();
        if ("300".equals(status)) {
            return InquiryOutcome.negative(type(), MISMATCH);
        }
        if (!"200".equals(status)) {
            throw new IllegalStateException("unknown status " + status);
        }
        JsonNode person = body.path("estelam3TPersonInfo").path(0);
        int alive = person.path("isaliveCode").asInt(-1);
        if (alive == 1) {
            return InquiryOutcome.negative(type(), DECEASED);
        }
        if (alive != 0) {
            throw new IllegalStateException("unknown alive code " + alive);
        }
        return InquiryOutcome.passed(type(), new CivilData(gender(person), birthDate(person)));
    }

    /** {@code genderField}: 0 is a woman («زن»), 1 a man; anything else cannot be used for Sepad's rule. */
    private static Gender gender(JsonNode person) {
        return switch (person.path("genderField").asInt(-1)) {
            case 0 -> Gender.FEMALE;
            case 1 -> Gender.MALE;
            default -> throw new IllegalStateException("unknown gender " + person.path("genderField"));
        };
    }

    /** {@code birthDateField} is a Jalali date as the number {@code yyyyMMdd}. */
    private static LocalDate birthDate(JsonNode person) {
        int value = person.path("birthDateField").asInt(0);
        return JalaliDate.of(value / 10000, value / 100 % 100, value % 100).toGregorian();
    }
}
