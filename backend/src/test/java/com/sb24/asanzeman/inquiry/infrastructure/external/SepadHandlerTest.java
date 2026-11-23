package com.sb24.asanzeman.inquiry.infrastructure.external;

import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.CONTEXT;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.answering;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.endpoint;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.failedBadResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.sb24.asanzeman.inquiry.domain.model.CivilData;
import com.sb24.asanzeman.inquiry.domain.model.Gender;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Sepad: who it applies to, and how the military-service answer is read. */
class SepadHandlerTest {

    private static final Instant TODAY = Instant.parse("2026-11-23T10:00:00Z"); // 2026-11-23 in Tehran

    private static SepadHandler handler(String body) {
        return new SepadHandler(answering(endpoint(Map.of()), 200, body), new MutableClock(TODAY));
    }

    private static InquiryOutcome run(String result, String description) {
        return handler("""
                {"payload":{"result":"%s","description":"%s","expirationDate":null,"errorCode":null,
                 "errorMessage":null},"errorResult":null,"conversationId":"x"}""".formatted(result, description))
                .execute(CONTEXT);
    }

    // ---- who it applies to ----------------------------------------------------------------------------------

    private static InquiryContext person(Gender gender, LocalDate born) {
        return CONTEXT.withCivil(new CivilData(gender, born));
    }

    @ParameterizedTest
    @CsvSource({"MALE,2008-11-23,true", "MALE,2008-11-24,false", "MALE,1976-11-23,true", "MALE,1975-11-24,true",
            "MALE,1975-11-23,false", "FEMALE,1990-01-01,false", "MALE,1990-01-01,true"})
    void onlyMenFromEighteenToFiftyInclusiveAreAsked(Gender gender, LocalDate born, boolean applies) {
        // 18 on the birthday itself (2008-11-23), still 50 on the day before the 51st birthday (1975-11-24)
        assertThat(handler("{}").appliesTo(person(gender, born))).isEqualTo(applies);
    }

    @Test
    void withoutCivilDataItDoesNotApply() {
        assertThat(handler("{}").appliesTo(new InquiryContext("1", "0912", 1, "0123"))).isFalse();
    }

    // ---- the answer -----------------------------------------------------------------------------------------

    @Test
    void permittedPasses() {
        assertThat(run("1", "مجاز")).isEqualTo(InquiryOutcome.passed(InquiryType.SEPAD));
    }

    @Test
    void notLiableForServicePassesEvenWithArabicLettersAndAFinalDot() {
        assertThat(run("0", "مشمولي با اطلاعات وارد شده يافت نشد.")).isEqualTo(InquiryOutcome.passed(InquiryType.SEPAD));
        assertThat(run("0", "مشمولی با اطلاعات وارد شده یافت نشد")).isEqualTo(InquiryOutcome.passed(InquiryType.SEPAD));
    }

    @Test
    void notAllowedIsNegative() {
        assertThat(run("0", "غيرمجاز")).isEqualTo(InquiryOutcome.negative(InquiryType.SEPAD,
                SepadHandler.NOT_ALLOWED));
        assertThat(run("0", "غیرمجاز.")).isEqualTo(InquiryOutcome.negative(InquiryType.SEPAD,
                SepadHandler.NOT_ALLOWED));
    }

    @Test
    void anyOtherTextOrResultIsNoAnswer() {
        assertThat(run("0", "در حال بررسی")).isEqualTo(failedBadResponse(InquiryType.SEPAD));
        assertThat(run("2", "مجاز")).isEqualTo(failedBadResponse(InquiryType.SEPAD));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"payload\":{\"result\":\"1\",\"errorCode\":\"E1\"}}",
            "{\"payload\":{\"result\":\"1\",\"errorMessage\":\"boom\"}}",
            "{\"payload\":{\"result\":\"1\"},\"errorResult\":{\"x\":1}}",
            "{\"payload\":null}", "{}", "not json"})
    void anErrorFieldOrAMissingPayloadIsNoAnswer(String body) {
        assertThat(handler(body).execute(CONTEXT)).isEqualTo(failedBadResponse(InquiryType.SEPAD));
    }

    @Test
    void aBadRequestFromTheServiceIsNoAnswer() {
        var h = new SepadHandler(answering(endpoint(Map.of()), 400, "{\"status\":20010012}"), new MutableClock(TODAY));

        assertThat(h.execute(CONTEXT)).isEqualTo(failedBadResponse(InquiryType.SEPAD));
    }

    @Test
    void theRequestHasTheJalaliBirthDateWithSlashesFromSabteAhval() {
        var client = answering(endpoint(Map.of()), 200, "{\"payload\":{\"result\":\"1\"}}");

        new SepadHandler(client, new MutableClock(TODAY)).execute(CONTEXT);

        verify(client).post(Map.of("birthDate", "1369/02/27", "nationalId", "1000000011"), Map.of());
    }
}
