package com.sb24.asanzeman.inquiry.infrastructure.external;

import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.CONTEXT;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.answering;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.endpoint;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.failedBadResponse;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.trackingFields;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/** Shahkar (mobile matches the national code) and the central bank's blacklist. */
class ShahkarAndBlacklistHandlerTest {

    // ---- Shahkar --------------------------------------------------------------------------------------------

    private static InquiryOutcome shahkar(int status, String body) {
        return new ShahkarHandler(answering(endpoint(trackingFields()), status, body)).execute(CONTEXT);
    }

    @Test
    void aMatchingMobilePassesEvenThoughTheIdFieldIsAlwaysTwoHundred() {
        assertThat(shahkar(200, "{\"response\":200,\"requestId\":\"1\",\"id\":\"200\",\"result\":\"OK.\"}"))
                .isEqualTo(InquiryOutcome.passed(InquiryType.SHAHKAR));
    }

    @Test
    void aMobileThatDoesNotMatchIsNegativeWithTheSrsMessage() {
        assertThat(shahkar(200, "{\"response\":600,\"id\":\"200\",\"result\":\"NotIdentifiedException\"}"))
                .isEqualTo(InquiryOutcome.negative(InquiryType.SHAHKAR, ShahkarHandler.MISMATCH));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"response\":311,\"result\":\"InvalidField:identificationNo;\"}", "{\"id\":\"200\"}",
            "<html></html>", ""})
    void anyOtherAnswerIsNoAnswer(String body) {
        assertThat(shahkar(200, body)).isEqualTo(failedBadResponse(InquiryType.SHAHKAR));
    }

    @Test
    void aValidationErrorFromTheServiceIsNoAnswer() {
        assertThat(shahkar(400, "{\"status\":400,\"errors\":{\"serviceNumber\":[\"required\"]}}"))
                .isEqualTo(failedBadResponse(InquiryType.SHAHKAR));
    }

    @Test
    void theRequestHasThePascalCaseTrackingBlockAndTextTypeFields() {
        var client = answering(endpoint(trackingFields()), 200, "{\"response\":200}");

        new ShahkarHandler(client).execute(CONTEXT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(client).post(body.capture(), any());
        assertThat(body.getValue()).containsEntry("serviceNumber", "09121234567")
                .containsEntry("identificationNo", "1000000011").containsEntry("identificationType", "0")
                .containsEntry("serviceType", "2")
                .containsEntry("ClientTrackingparam", Map.of("UserName", "azs", "Ip", "10.0.0.1",
                        "BranchCode", "0123", "ChanelCode", 3));
    }

    // ---- Blacklist ------------------------------------------------------------------------------------------

    private static InquiryOutcome blacklist(int status, String body) {
        return new BlacklistHandler(answering(endpoint(Map.of()), status, body)).execute(CONTEXT);
    }

    @Test
    void notOnTheBlacklistPasses() {
        assertThat(blacklist(200, "{\"result\":false}")).isEqualTo(InquiryOutcome.passed(InquiryType.BLACKLIST));
    }

    @Test
    void onTheBlacklistIsNegative() {
        assertThat(blacklist(200, "{\"result\":true}")).isEqualTo(
                InquiryOutcome.negative(InquiryType.BLACKLIST, "inquiry.central-bank-restriction"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"result\":null}", "{\"result\":\"false\"}", "not json"})
    void aMissingOrUnclearResultIsNoAnswer(String body) {
        assertThat(blacklist(200, body)).isEqualTo(failedBadResponse(InquiryType.BLACKLIST));
    }

    @Test
    void anHttpErrorIsNoAnswer() {
        assertThat(blacklist(500, "{}")).isEqualTo(failedBadResponse(InquiryType.BLACKLIST));
    }

    @Test
    void theRequestIsTheNationalKey() {
        var client = answering(endpoint(Map.of()), 200, "{\"result\":false}");

        new BlacklistHandler(client).execute(CONTEXT);

        verify(client).post(Map.of("nationalKey", "1000000011"), Map.of());
    }
}
