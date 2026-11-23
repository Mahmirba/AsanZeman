package com.sb24.asanzeman.inquiry.infrastructure.external;

import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.CONTEXT;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.answering;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.endpoint;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.failedBadResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The two currency-commitment inquiries, which read "nothing found" in opposite ways (5.3 #8 and #9). */
class CommitmentHandlersTest {

    // ---- Import commitments ---------------------------------------------------------------------------------

    private static InquiryOutcome imports(int status, String body) {
        return new ImportCommitmentHandler(answering(endpoint(Map.of()), status, body)).execute(CONTEXT);
    }

    private static String rows(String... allowed) {
        var items = new java.util.ArrayList<String>();
        for (String a : allowed) {
            items.add("{\"darsadTaahodArziRafNashodeh\":\"0\",\"mablagh\":\"0\",\"izMojaz\":" + a + "}");
        }
        return "{\"returnData\":[" + String.join(",", items) + "]}";
    }

    @Test
    void everyCommitmentAllowedPasses() {
        assertThat(imports(200, rows("true", "true"))).isEqualTo(InquiryOutcome.passed(InquiryType.IMPORT_COMMITMENT));
    }

    @Test
    void anyCommitmentNotAllowedIsNegative() {
        assertThat(imports(200, rows("true", "false"))).isEqualTo(
                InquiryOutcome.negative(InquiryType.IMPORT_COMMITMENT, "inquiry.central-bank-restriction"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"returnData\":[]}", "{\"returnData\":null}", "{}",
            "{\"returnData\":[{\"mablagh\":\"0\"}]}", "{\"returnData\":[{\"izMojaz\":\"true\"}]}", "not json"})
    void noCommitmentsOrAnUnclearOneIsNoAnswerNotAllClear(String body) {
        assertThat(imports(200, body)).isEqualTo(failedBadResponse(InquiryType.IMPORT_COMMITMENT));
    }

    @Test
    void aNotFoundIsNoAnswer() {
        assertThat(imports(404, "{\"status\":404,\"detail\":\"x\"}"))
                .isEqualTo(failedBadResponse(InquiryType.IMPORT_COMMITMENT));
    }

    @Test
    void theChannelCodeGoesInAHeaderAndTheNationalCodeInTheBody() {
        var client = answering(endpoint(Map.of()), 200, rows("true"));

        new ImportCommitmentHandler(client).execute(CONTEXT);

        verify(client).post(Map.of("shenasehMelliOrShomarehKartBazargani", "1000000011"),
                Map.of("ChanelCode", "15"));
    }

    @Test
    void theChannelCodeCanBeConfigured() {
        var client = answering(endpoint(Map.of("channel-code", "21")), 200, rows("true"));

        new ImportCommitmentHandler(client).execute(CONTEXT);

        verify(client).post(Map.of("shenasehMelliOrShomarehKartBazargani", "1000000011"),
                Map.of("ChanelCode", "21"));
    }

    // ---- Export commitments ---------------------------------------------------------------------------------

    private static InquiryOutcome exports(int status, String body) {
        return new ExportCommitmentHandler(answering(endpoint(Map.of()), status, body)).execute(CONTEXT);
    }

    @Test
    void anExporterWithTheCommitmentCreatedPasses() {
        assertThat(exports(200, "{\"national_id\":\"1\",\"commitment_percentage\":81.54,\"commitment_creation\":true}"))
                .isEqualTo(InquiryOutcome.passed(InquiryType.EXPORT_COMMITMENT));
    }

    @Test
    void anExporterWithoutTheCommitmentCreatedIsNegative() {
        assertThat(exports(200, "{\"commitment_creation\":false}")).isEqualTo(
                InquiryOutcome.negative(InquiryType.EXPORT_COMMITMENT, "inquiry.central-bank-restriction"));
    }

    @Test
    void notBeingAnExporterPassesWhichIsTheOppositeOfTheImportInquiry() {
        assertThat(exports(404, "{\"status\":404,\"detail\":\"No MerchantCommitment matches the given query.\"}"))
                .isEqualTo(InquiryOutcome.passed(InquiryType.EXPORT_COMMITMENT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":404,\"detail\":\"Not Found\"}", "{\"status\":404}", "not json"})
    void aNotFoundWithAnyOtherDetailIsAWrongUrlAndNeverApprovesTheCustomer(String body) {
        assertThat(exports(404, body)).isEqualTo(failedBadResponse(InquiryType.EXPORT_COMMITMENT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"commitment_creation\":null}", "{\"commitment_creation\":\"true\"}", "not json"})
    void aMissingFlagIsNoAnswer(String body) {
        assertThat(exports(200, body)).isEqualTo(failedBadResponse(InquiryType.EXPORT_COMMITMENT));
    }

    @Test
    void aServerErrorIsNoAnswer() {
        assertThat(exports(500, "{}")).isEqualTo(failedBadResponse(InquiryType.EXPORT_COMMITMENT));
    }

    @Test
    void theNationalCodeGoesInThePathWithNoBody() {
        var client = answering(endpoint(Map.of()), 200, "{\"commitment_creation\":true}");

        new ExportCommitmentHandler(client).execute(CONTEXT);

        verify(client).get("/1000000011");
    }
}
