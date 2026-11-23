package com.sb24.asanzeman.inquiry.infrastructure.external;

import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.CONTEXT;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.answering;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.endpoint;
import static com.sb24.asanzeman.inquiry.infrastructure.external.HandlerFixtures.failedBadResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Sama Cheque (bounced cheques), Samat (non-current loans) and the board-relation credit limits. */
class FinancialHandlersTest {

    // ---- Sama Cheque ----------------------------------------------------------------------------------------

    private static InquiryOutcome cheques(int status, String body) {
        return new SamaChequeHandler(answering(endpoint(Map.of()), status, body)).execute(CONTEXT);
    }

    @Test
    void noBouncedChequesPasses() {
        assertThat(cheques(200, "{\"cheques\":[],\"errors\":null,\"isValid\":true}"))
                .isEqualTo(InquiryOutcome.passed(InquiryType.SAMA_CHEQUE));
    }

    @Test
    void aBouncedChequeIsNegative() {
        assertThat(cheques(200, "{\"cheques\":[{\"amount\":200000000,\"serial\":\"0000877508      \"}],"
                + "\"errors\":null,\"isValid\":true}"))
                .isEqualTo(InquiryOutcome.negative(InquiryType.SAMA_CHEQUE, "inquiry.sama-cheque.bounced"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"cheques\":null,\"errors\":[{\"errorCode\":\"chq402\",\"field\":\"NationalId\"}],\"isValid\":false}",
            "{\"cheques\":null,\"errors\":[{\"errorCode\":\"chq453\",\"field\":\"\"}],\"isValid\":false}",
            "{\"cheques\":null,\"isValid\":true}", "{\"cheques\":[]}", "{}", "not json"})
    void anErrorOrAMissingListIsNoAnswer(String body) {
        assertThat(cheques(200, body)).isEqualTo(failedBadResponse(InquiryType.SAMA_CHEQUE));
    }

    @Test
    void theRequestHasThePersonAndTheFixedFields() {
        var client = answering(endpoint(Map.of("person-type", "1", "request-info", "1")), 200,
                "{\"cheques\":[],\"isValid\":true}");

        new SamaChequeHandler(client).execute(CONTEXT);

        verify(client).post(Map.of("personInfo", Map.of("nationalId", "1000000011", "personType", 1),
                "requestInfo", "1"), Map.of());
    }

    // ---- Samat ----------------------------------------------------------------------------------------------

    private static InquiryOutcome samat(String body) {
        return new SamatHandler(answering(endpoint(Map.of()), 200, body)).execute(CONTEXT);
    }

    private static String loans(String... rows) {
        return "{\"returnValue\":{\"estelamAsliRows\":[" + String.join(",", rows)
                + "],\"nationalCd\":\"1000000011\"},\"hasError\":false,\"errors\":null}";
    }

    private static String row(String overdue, String deferred, String doubtful) {
        return "{\"amBedehiKol\":152621376,\"amSarResid\":%s,\"amMoavagh\":%s,\"amMashkuk\":%s,\"amSukht\":99}"
                .formatted(overdue, deferred, doubtful);
    }

    @Test
    void loansWithNothingOverdueDeferredOrDoubtfulPass() {
        assertThat(samat(loans(row("0", "0", "0"), row("0", "0.00000", "0"))))
                .isEqualTo(InquiryOutcome.passed(InquiryType.SAMAT));
    }

    @Test
    void noLoansAtAllPassesWhetherTheListIsEmptyOrNull() {
        assertThat(samat(loans())).isEqualTo(InquiryOutcome.passed(InquiryType.SAMAT));
        assertThat(samat("{\"returnValue\":{\"estelamAsliRows\":null},\"hasError\":false}"))
                .isEqualTo(InquiryOutcome.passed(InquiryType.SAMAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "0.5", "123456789.00000"})
    void anyOverdueAmountOnAnyLoanIsNegative(String overdue) {
        assertThat(samat(loans(row("0", "0", "0"), row(overdue, "0", "0"))))
                .isEqualTo(InquiryOutcome.negative(InquiryType.SAMAT, "inquiry.samat.non-current"));
    }

    @Test
    void deferredAndDoubtfulAmountsCountToo() {
        assertThat(samat(loans(row("0", "5", "0"))).result().name()).isEqualTo("NEGATIVE");
        assertThat(samat(loans(row("0", "0", "5"))).result().name()).isEqualTo("NEGATIVE");
    }

    @Test
    void theFuelAmountDoesNotCount() {
        assertThat(samat(loans("{\"amSarResid\":0,\"amMoavagh\":0,\"amMashkuk\":0,\"amSukht\":500}")))
                .isEqualTo(InquiryOutcome.passed(InquiryType.SAMAT));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"returnValue\":null,\"hasError\":true,\"errors\":[{\"errorCd\":\"11\",\"errorDsc\":\"cstmrShenase\"}]}",
            "{\"returnValue\":{},\"hasError\":false}", "{\"hasError\":false}",
            "{\"returnValue\":{\"estelamAsliRows\":[{\"amSarResid\":0,\"amMoavagh\":0}]}}",
            "{\"returnValue\":{\"estelamAsliRows\":[{\"amSarResid\":\"0\",\"amMoavagh\":0,\"amMashkuk\":0}]}}",
            "not json"})
    void anErrorOrAnUnreadableLoanIsNoAnswer(String body) {
        assertThat(samat(body)).isEqualTo(failedBadResponse(InquiryType.SAMAT));
    }

    @Test
    void theRequestHasTheNationalCodeAsTheCustomerId() {
        var client = answering(endpoint(Map.of()), 200, loans());

        new SamatHandler(client).execute(CONTEXT);

        var expected = new LinkedHashMap<String, Object>();
        expected.put("estlmId", "1");
        expected.put("cstmrType", "1");
        expected.put("cstmrShenase", "1000000011");
        verify(client).post(expected, Map.of());
    }

    // ---- Board relation -------------------------------------------------------------------------------------

    private static InquiryOutcome board(int status, String body) {
        return new BoardRelationHandler(answering(endpoint(Map.of()), status, body)).execute(CONTEXT);
    }

    @Test
    void insideTheLimitsPasses() {
        assertThat(board(200, "{\"trackingCode\":\"t\",\"description\":\"ok\",\"result\":true}"))
                .isEqualTo(InquiryOutcome.passed(InquiryType.BOARD_RELATION));
    }

    @Test
    void aBrokenLimitIsNegative() {
        assertThat(board(200, "{\"description\":\"limit broken\",\"result\":false}"))
                .isEqualTo(InquiryOutcome.negative(InquiryType.BOARD_RELATION, "inquiry.central-bank-restriction"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"result\":null}", "{\"result\":\"true\"}", "not json"})
    void aMissingResultIsNoAnswer(String body) {
        assertThat(board(200, body)).isEqualTo(failedBadResponse(InquiryType.BOARD_RELATION));
    }

    @Test
    void aBadRequestIsNoAnswer() {
        assertThat(board(400, "{\"status\":400,\"detail\":\"0 Bad Request\"}"))
                .isEqualTo(failedBadResponse(InquiryType.BOARD_RELATION));
    }

    @Test
    void theRequestHasTheAmountAndTheJalaliBirthDateWithoutSlashes() {
        var client = answering(endpoint(Map.of()), 200, "{\"result\":true}");

        new BoardRelationHandler(client).execute(CONTEXT);

        var expected = new LinkedHashMap<String, Object>();
        expected.put("amount", 250_000_000L);
        expected.put("customerType", 1);
        expected.put("nationalIdOrLegalId", "1000000011");
        expected.put("birthDateOrRegisterDate", "13690227");
        verify(client).post(expected, Map.of());
    }
}
