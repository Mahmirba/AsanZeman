package com.sb24.asanzeman.inquiry.infrastructure.external;

import com.sb24.asanzeman.inquiry.domain.model.Gender;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.integration.inquiry.InquiryClient;
import com.sb24.asanzeman.integration.inquiry.InquiryReply;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/**
 * Sepad, the military-service status (5.3 #3). It only applies to men aged 18 to 50 inclusive, by Sabte Ahval's gender
 * and birth date (D26). The answer is {@code payload.result}:
 *
 * <ul>
 *   <li>{@code "1"} (مجاز): {@code PASSED};</li>
 *   <li>{@code "0"} with «مشمولی با اطلاعات وارد شده یافت نشد» (not liable for service): {@code PASSED};</li>
 *   <li>{@code "0"} with «غیرمجاز»: {@code NEGATIVE};</li>
 *   <li>{@code "0"} with any other text, or any {@code errorCode}, {@code errorMessage} or {@code errorResult}:
 *       {@code FAILED}.</li>
 * </ul>
 *
 * The service writes Arabic letters and sometimes a final dot, so the text is normalised before it is compared.
 */
public class SepadHandler extends JsonInquiryHandler {

    public static final String NOT_ALLOWED = "inquiry.sepad.not-allowed";
    static final String NOT_LIABLE_TEXT = "مشمولی با اطلاعات وارد شده یافت نشد";
    static final String NOT_ALLOWED_TEXT = "غیرمجاز";
    static final int MIN_AGE = 18;
    static final int MAX_AGE = 50;
    private static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");

    private final Clock clock;

    public SepadHandler(InquiryClient client, Clock clock) {
        super(InquiryType.SEPAD, client);
        this.clock = clock;
    }

    /** Men from 18 up to and including 50. */
    @Override
    public boolean appliesTo(InquiryContext context) {
        if (context.civil() == null || context.civil().gender() != Gender.MALE) {
            return false;
        }
        int age = context.ageOn(clock.instant().atZone(TEHRAN).toLocalDate());
        return age >= MIN_AGE && age <= MAX_AGE;
    }

    @Override
    InquiryReply ask(InquiryContext context) {
        return client.post(Map.of("birthDate", JalaliDate.fromGregorian(context.civil().birthDate()).toSlashed(),
                "nationalId", context.nationalCode()), Map.of());
    }

    @Override
    InquiryOutcome decide(InquiryContext context, InquiryReply reply) {
        requireOk(reply);
        JsonNode body = json(reply);
        JsonNode payload = body.path("payload");
        if (present(body.path("errorResult")) || present(payload.path("errorCode"))
                || present(payload.path("errorMessage"))) {
            throw new IllegalStateException("the service reports an error");
        }
        String result = payload.path("result").asString();
        if ("1".equals(result)) {
            return InquiryOutcome.passed(type());
        }
        if (!"0".equals(result)) {
            throw new IllegalStateException("unknown result " + result);
        }
        String description = PersianText.normalise(payload.path("description").asString());
        if (description.equals(PersianText.normalise(NOT_LIABLE_TEXT))) {
            return InquiryOutcome.passed(type());
        }
        if (description.equals(PersianText.normalise(NOT_ALLOWED_TEXT))) {
            return InquiryOutcome.negative(type(), NOT_ALLOWED);
        }
        throw new IllegalStateException("unknown description " + description);
    }
}
