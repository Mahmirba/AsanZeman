package com.sb24.asanzeman.inquiry.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.inquiry.domain.model.CivilData;
import com.sb24.asanzeman.inquiry.domain.model.FailureKind;
import com.sb24.asanzeman.inquiry.domain.model.Gender;
import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryResult;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.integration.inquiry.InquiryCallException;
import com.sb24.asanzeman.integration.inquiry.InquiryClient;
import com.sb24.asanzeman.integration.inquiry.InquiryEndpoint;
import com.sb24.asanzeman.integration.inquiry.InquiryReply;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class SabtAhvalHandlerTest {

    private static final InquiryContext CONTEXT = new InquiryContext("1000000011", "09121234567", 1000, "0123",
            LocalDate.of(1990, 5, 17)); // Jalali 1369/02/27

    private InquiryClient client;
    private SabtAhvalHandler handler;

    @BeforeEach
    void setUp() {
        client = mock(InquiryClient.class);
        when(client.endpoint()).thenReturn(endpoint("REQUEST"));
        handler = new SabtAhvalHandler(client);
    }

    private static InquiryEndpoint endpoint(String branch) {
        return new InquiryEndpoint(null, "/x", Duration.ofSeconds(15), 2, Map.of("user-name", "azs", "ip", "10.0.0.1",
                "branch-code", branch, "channel-code", "1", "request-type", "1"));
    }

    private static String answer(String status, String alive, String gender, String birth, boolean hasError) {
        return """
                {"estelam3TPersonInfo":[{"birthDateField":%s,"genderField":%s,"isaliveCode":%s}],
                 "serviceStatus":{"statusField":"%s","messageField":"single.record"},"hasError":%s,"errorList":null}"""
                .formatted(birth, gender, alive, status, hasError);
    }

    private InquiryOutcome run(int http, String body) {
        when(client.post(any(), any())).thenReturn(new InquiryReply(http, body));
        return handler.execute(CONTEXT);
    }

    @Test
    void aLivingMatchPassesAndGivesTheRegistrysGenderAndBirthDate() {
        var outcome = run(200, answer("200", "0", "1", "13690227", false));

        assertThat(outcome).isEqualTo(InquiryOutcome.passed(InquiryType.SABT_AHVAL,
                new CivilData(Gender.MALE, LocalDate.of(1990, 5, 17))));
    }

    @Test
    void zeroIsAWoman() {
        var outcome = run(200, answer("200", "0", "0", "13651212", false));

        assertThat(outcome.civil().gender()).isEqualTo(Gender.FEMALE);
        assertThat(outcome.civil().birthDate()).isEqualTo(LocalDate.of(1987, 3, 3));
    }

    @Test
    void aDeceasedPersonIsNegative() {
        assertThat(run(200, answer("200", "1", "1", "13690227", false)))
                .isEqualTo(InquiryOutcome.negative(InquiryType.SABT_AHVAL, SabtAhvalHandler.DECEASED));
    }

    @Test
    void aNationalCodeThatDoesNotMatchTheBirthDateIsNegativeEvenThoughHasErrorIsFalse() {
        var body = "{\"estelam3TPersonInfo\":null,\"serviceStatus\":{\"statusField\":\"300\","
                + "\"messageField\":\"err.record.not.found\"},\"hasError\":false,\"errorList\":null}";

        assertThat(run(200, body)).isEqualTo(InquiryOutcome.negative(InquiryType.SABT_AHVAL,
                SabtAhvalHandler.MISMATCH));
    }

    @Test
    void anErrorFlagIsFailedNotNegative() {
        assertThat(run(200, answer("200", "0", "1", "13690227", true)))
                .isEqualTo(InquiryOutcome.failed(InquiryType.SABT_AHVAL, FailureKind.BAD_RESPONSE));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // an unknown status
            "{\"serviceStatus\":{\"statusField\":\"500\"},\"hasError\":false}",
            // an unknown alive code, an unknown gender, an unreadable birth date
            "{\"estelam3TPersonInfo\":[{\"birthDateField\":13690227,\"genderField\":1,\"isaliveCode\":2}],\"serviceStatus\":{\"statusField\":\"200\"}}",
            "{\"estelam3TPersonInfo\":[{\"birthDateField\":13690227,\"genderField\":7,\"isaliveCode\":0}],\"serviceStatus\":{\"statusField\":\"200\"}}",
            "{\"estelam3TPersonInfo\":[{\"birthDateField\":13691399,\"genderField\":1,\"isaliveCode\":0}],\"serviceStatus\":{\"statusField\":\"200\"}}",
            "{\"estelam3TPersonInfo\":[],\"serviceStatus\":{\"statusField\":\"200\"}}",
            // not an object, not JSON, empty
            "[1,2]", "<html>proxy error</html>", ""})
    void anythingUnclearIsFailedNeverARejection(String body) {
        assertThat(run(200, body)).isEqualTo(InquiryOutcome.failed(InquiryType.SABT_AHVAL, FailureKind.BAD_RESPONSE));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 404, 500})
    void anHttpStatusOtherThan200IsFailed(int status) {
        assertThat(run(status, "{\"detail\":\"x\"}"))
                .isEqualTo(InquiryOutcome.failed(InquiryType.SABT_AHVAL, FailureKind.BAD_RESPONSE));
    }

    @Test
    void aServiceThatDoesNotAnswerIsFailedWithTheKind() {
        when(client.post(any(), any())).thenThrow(new InquiryCallException(InquiryCallException.Kind.TIMEOUT, null),
                new InquiryCallException(InquiryCallException.Kind.UNAVAILABLE, null));

        assertThat(handler.execute(CONTEXT).failure()).isEqualTo(FailureKind.TIMEOUT);
        assertThat(handler.execute(CONTEXT).failure()).isEqualTo(FailureKind.UNAVAILABLE);
    }

    @Test
    void theRequestCarriesTheJalaliBirthDateTheBranchOfTheRequestAndTheConfiguredFields() {
        run(200, answer("200", "0", "1", "13690227", false));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(client).post(body.capture(), eq(Map.of()));
        assertThat(body.getValue()).containsEntry("sabtParam", Map.of("birthDate", "13690227",
                "nationalCode", "1000000011", "requestType", 1));
        assertThat(body.getValue()).containsEntry("clientTrackingParam", Map.of("userName", "azs", "ip", "10.0.0.1",
                "branchCode", "0123", "chanelCode", 1));
    }

    @Test
    void aFixedBranchCodeInTheConfigurationIsUsedInstead() {
        when(client.endpoint()).thenReturn(endpoint("330"));
        run(200, answer("200", "0", "1", "13690227", false));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(client).post(body.capture(), any());
        @SuppressWarnings("unchecked")
        var tracking = (Map<String, Object>) body.getValue().get("clientTrackingParam");
        assertThat(tracking).containsEntry("branchCode", "330");
    }

    @Test
    void aRequestWithoutABirthDateCannotBeCheckedSoItIsFailed() {
        var outcome = handler.execute(new InquiryContext("1000000011", "09121234567", 1000, "0123"));

        assertThat(outcome.result()).isEqualTo(InquiryResult.FAILED);
        assertThat(outcome.failure()).isEqualTo(FailureKind.UNAVAILABLE);
    }
}
