package com.sb24.asanzeman.integration.yaghut;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.response.DefaultResponseCreator;
import org.springframework.web.client.RestClient;

class YaghutClientTest {

    private static final String BASE = "https://yaghut.test";
    private static final String LOGIN_URL = BASE + "/user/loginStatic";
    private static final String BRANCHES_URL = BASE + "/branches";

    /** The real loginStatic answer of the bank's test environment. */
    private static final String LOGIN_JSON = """
            {"result": {"sessionId": "6379c721-1c8d-4936-b7fa-b88b02c1dc09", "temporarySessionId": null,
             "lastLoginTime": "2026-11-14T09:31:01IRST", "gender": null, "name": "کاربر محيط تست سامانه ريچک",
             "foreignName": "x", "title": null, "code": null, "refreshToken": null, "subscribers": null,
             "cif": null, "mobile": null, "email": null, "temporaryCause": null,
             "sessionExpirationDate": "2026-11-16T13:51:10IRST", "refreshTokenExpirationDate": null,
             "passwordExpirationBean": null, "userIsNew": null}}""";

    private MockRestServiceServer server;
    private YaghutClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new YaghutClient(builder.build(), BASE);
    }

    private static DefaultResponseCreator json(String body) {
        return withSuccess(body, MediaType.APPLICATION_JSON);
    }

    private static DefaultResponseCreator fault(HttpStatus status, String exception, String message) {
        return withStatus(status).contentType(MediaType.APPLICATION_JSON).body("{\"exception\":\"" + exception
                + "\",\"exception_params\":\"{}\",\"success\":false,\"exception_message\":\"" + message + "\"}");
    }

    // ---- loginStatic -------------------------------------------------------------------------------------------

    @Test
    void loginSendsTheCredentialsAndTakesTheSessionFromTheCookie() {
        server.expect(requestTo(LOGIN_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist(HttpHeaders.COOKIE))
                .andExpect(content().json("{\"username\":\"svc\",\"password\":\"pw\"}"))
                .andRespond(json(LOGIN_JSON).header(HttpHeaders.SET_COOKIE, "BIGipServerPool=123; path=/")
                        .header(HttpHeaders.SET_COOKIE, "JSESSIONID=ABC123; Path=/; HttpOnly"));

        YaghutLogin login = client.login("svc", "pw");

        // the body's sessionId is ignored: only the JSESSIONID cookie counts
        assertThat(login.session().jsessionId()).isEqualTo("ABC123");
        assertThat(login.session().expiresAt()).isEqualTo(Instant.parse("2026-11-16T10:21:10Z"));
        assertThat(login.name()).isEqualTo("کاربر محيط تست سامانه ريچک");
        assertThat(login.cif()).isNull();
        assertThat(login.code()).isNull();
        assertThat(login.temporarySessionId()).isNull();
        assertThat(login.passwordExpirationJson()).isNull();
        server.verify();
    }

    @Test
    void loginReadsAnAgencyAndKeepsTheRawPasswordExpiration() {
        server.expect(requestTo(LOGIN_URL)).andRespond(json("""
                {"result": {"cif": "9001", "code": "0012345678", "name": "Agency", "temporarySessionId": "T1",
                 "temporaryCause": "TICKET_LOGIN", "sessionExpirationDate": null,
                 "passwordExpirationBean": {"expired": true}}}""")
                .header(HttpHeaders.SET_COOKIE, "JSESSIONID=S9"));

        YaghutLogin login = client.login("agency", "pw");

        assertThat(login.cif()).isEqualTo("9001");
        assertThat(login.code()).isEqualTo("0012345678");
        assertThat(login.temporarySessionId()).isEqualTo("T1");
        assertThat(login.temporaryCause()).isEqualTo("TICKET_LOGIN");
        assertThat(login.session().expiresAt()).isNull();
        assertThat(login.passwordExpirationJson()).isEqualTo("{\"expired\":true}");
    }

    @Test
    void loginWithoutAJsessionidIsATechnicalError() {
        server.expect(requestTo(LOGIN_URL)).andRespond(json(LOGIN_JSON)
                .header(HttpHeaders.SET_COOKIE, "other=1; path=/").header(HttpHeaders.SET_COOKIE, "JSESSIONID=; Path=/"));

        assertThatThrownBy(() -> client.login("svc", "pw")).isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void loginWithAnUnreadableExpiryIsATechnicalError() {
        server.expect(requestTo(LOGIN_URL)).andRespond(
                json("{\"result\":{\"sessionExpirationDate\":\"soon\"}}")
                        .header(HttpHeaders.SET_COOKIE, "JSESSIONID=S1"));

        assertThatThrownBy(() -> client.login("svc", "pw")).isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void invalidCredentialsIsAFault() {
        server.expect(requestTo(LOGIN_URL))
                .andRespond(fault(HttpStatus.OK, "InvalidCredentialsWSException", "bad password"));

        assertThatThrownBy(() -> client.login("svc", "bad")).isInstanceOfSatisfying(YaghutException.class,
                ex -> assertThat(ex.fault()).isEqualTo(YaghutException.Fault.INVALID_CREDENTIALS));
    }

    @Test
    void suspectedCustomerIsAFault() {
        server.expect(requestTo(LOGIN_URL))
                .andRespond(fault(HttpStatus.OK, "SuspectedCustomerWSException", "suspected"));

        assertThatThrownBy(() -> client.login("c", "pw")).isInstanceOfSatisfying(YaghutException.class,
                ex -> assertThat(ex.fault()).isEqualTo(YaghutException.Fault.SUSPECTED_CUSTOMER));
    }

    // ---- faults, whatever the HTTP status ----------------------------------------------------------------------

    @Test
    void successFalseIsAFaultEvenOnAServerErrorStatus() {
        server.expect(requestTo(BRANCHES_URL)).andRespond(
                fault(HttpStatus.INTERNAL_SERVER_ERROR, "InvalidSessionWSException", "session ended"));

        assertThatThrownBy(() -> client.branches(new YaghutSession("S1", null), 0, 10))
                .isInstanceOfSatisfying(YaghutException.class,
                        ex -> assertThat(ex.fault()).isEqualTo(YaghutException.Fault.INVALID_SESSION));
    }

    @Test
    void serviceAccessDeniedIsAFault() {
        server.expect(requestTo(BRANCHES_URL)).andRespond(fault(HttpStatus.OK, "ServiceAccessIsDeniedWSException",
                "Principal is not active for Service Id 12 Channel Id 3"));

        assertThatThrownBy(() -> client.branches(new YaghutSession("S1", null), 0, 10))
                .isInstanceOfSatisfying(YaghutException.class, ex -> {
                    assertThat(ex.fault()).isEqualTo(YaghutException.Fault.SERVICE_ACCESS_DENIED);
                    assertThat(ex).hasMessageContaining("Service Id 12 Channel Id 3");
                });
    }

    @Test
    void anUnknownFaultIsTechnical() {
        server.expect(requestTo(BRANCHES_URL)).andRespond(fault(HttpStatus.OK, "WebserviceException", "boom"));

        assertThatThrownBy(() -> client.branches(new YaghutSession("S1", null), 0, 10))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void aFaultWithoutAnExceptionNameIsTechnical() {
        server.expect(requestTo(BRANCHES_URL)).andRespond(json("{\"success\":false}"));

        assertThatThrownBy(() -> client.branches(new YaghutSession("S1", null), 0, 10))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void aProxyHtmlPageIsTechnical() {
        server.expect(requestTo(BRANCHES_URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.TEXT_HTML).body("<html>502</html>"));

        assertThatThrownBy(() -> client.branches(new YaghutSession("S1", null), 0, 10))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void aJsonArrayInsteadOfAnObjectIsTechnical() {
        server.expect(requestTo(BRANCHES_URL)).andRespond(json("[1,2]"));

        assertThatThrownBy(() -> client.branches(new YaghutSession("S1", null), 0, 10))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void anEmptyBodyIsTechnical() {
        server.expect(requestTo(BRANCHES_URL)).andRespond(withSuccess());

        assertThatThrownBy(() -> client.branches(new YaghutSession("S1", null), 0, 10))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void aServerErrorWithAnOrdinaryJsonBodyIsTechnical() {
        server.expect(requestTo(BRANCHES_URL)).andRespond(
                withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThatThrownBy(() -> client.branches(new YaghutSession("S1", null), 0, 10))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void aTimeoutIsTechnical() {
        server.expect(requestTo(BRANCHES_URL)).andRespond(withException(new IOException("read timed out")));

        assertThatThrownBy(() -> client.branches(new YaghutSession("S1", null), 0, 10))
                .isInstanceOf(TemporarilyUnavailableException.class).hasRootCauseMessage("read timed out");
    }

    // ---- branches ----------------------------------------------------------------------------------------------

    @Test
    void branchesSendsOnlyTheSessionCookieAndReadsAPage() {
        server.expect(requestTo(BRANCHES_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.COOKIE, "JSESSIONID=S1"))
                .andExpect(content().json("{\"offset\":20,\"length\":10}"))
                .andRespond(json("""
                        {"result": {"totalRecord": 3, "branches": [
                          {"branchCode": "0123", "name": "Main", "city": "Tehran", "address": "A", "tel": "1",
                           "fax": "2", "zipCode": "3"},
                          {"branchCode": 456, "name": "Second"}]}}"""));

        YaghutBranchPage page = client.branches(new YaghutSession("S1", null), 20, 10);

        assertThat(page.totalRecord()).isEqualTo(3);
        assertThat(page.branches()).hasSize(2);
        // the code is a string, exactly as sent: no zero stripping (D32)
        assertThat(page.branches().get(0)).isEqualTo(
                new YaghutBranchPage.Branch("0123", "Main", "Tehran", "A", "1", "2", "3"));
        assertThat(page.branches().get(1).branchCode()).isEqualTo("456");
        assertThat(page.branches().get(1).city()).isNull();
        server.verify();
    }

    @Test
    void branchesAcceptsAnAnswerWithoutAResultWrapper() {
        server.expect(requestTo(BRANCHES_URL)).andRespond(json("{\"totalRecord\":1,\"branches\":[{\"branchCode\":\"7\"}]}"));

        YaghutBranchPage page = client.branches(new YaghutSession("S1", null), 0, 10);

        assertThat(page.branches()).extracting(YaghutBranchPage.Branch::branchCode).containsExactly("7");
    }

    @Test
    void aGenuinelyEmptyPageIsAccepted() {
        server.expect(requestTo(BRANCHES_URL)).andRespond(json("{\"result\":{\"totalRecord\":0}}"));

        YaghutBranchPage page = client.branches(new YaghutSession("S1", null), 0, 10);

        assertThat(page.totalRecord()).isZero();
        assertThat(page.branches()).isEmpty();
    }

    @Test
    void branchesWithoutATotalIsATechnicalErrorNotAnEmptyList() {
        // BranchSyncJob deactivates branches Yaghut no longer returns, so a malformed answer must never look empty
        server.expect(requestTo(BRANCHES_URL)).andRespond(json("{\"result\":{}}"));
        server.expect(requestTo(BRANCHES_URL)).andRespond(json("{\"result\":null}"));

        assertThatThrownBy(() -> client.branches(new YaghutSession("S1", null), 0, 10))
                .isInstanceOf(TemporarilyUnavailableException.class);
        assertThatThrownBy(() -> client.branches(new YaghutSession("S1", null), 0, 10))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    // ---- customerDetailInfo ------------------------------------------------------------------------------------

    private static final String CUSTOMER_URL = BASE + "/customerDetailInfo";

    @Test
    void customerDetailInfoSendsTheNationalCodeWithTheCallersSessionAndReadsTheSnapshot() {
        server.expect(requestTo(CUSTOMER_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.COOKIE, "JSESSIONID=S1"))
                .andExpect(content().json("{\"code\":\"0012345678\"}", true))
                .andRespond(json("""
                        {"result": {"cif": "555", "code": "0012345678", "firstName": "Ali", "lastName": "Rezaei",
                         "fatherName": "Reza", "birthDate": "1990-05-17T00:00:00IRST",
                         "mobile": "09121234567", "ssn": "123", "certificateSerial": "S", "certificateSeries": "T",
                         "personalityType": "ACTUAL", "gender": "MALE"}}"""));

        YaghutCustomer customer = client.customerDetailInfo(new YaghutSession("S1", null), "0012345678");

        assertThat(customer).isEqualTo(new YaghutCustomer("555", "0012345678", "Ali", "Rezaei", "Ali Rezaei", "Reza",
                java.time.LocalDate.of(1990, 5, 17), "09121234567", "123", "S", "T", "ACTUAL", "MALE"));
    }

    @Test
    void theFullNameIsFirstAndLastNameTogetherOrTheTitleWhenThereAreNeither() {
        server.expect(requestTo(CUSTOMER_URL)).andRespond(json("{\"result\": {\"lastName\": \"Rezaei\"}}"));
        server.expect(requestTo(CUSTOMER_URL)).andRespond(json("{\"result\": {\"firstName\": \"Ali\"}}"));
        server.expect(requestTo(CUSTOMER_URL)).andRespond(json("{\"result\": {\"title\": \"Sky Air Co\"}}"));

        var session = new YaghutSession("S1", null);

        assertThat(client.customerDetailInfo(session, "1").fullName()).isEqualTo("Rezaei");
        assertThat(client.customerDetailInfo(session, "1").fullName()).isEqualTo("Ali");
        assertThat(client.customerDetailInfo(session, "1").fullName()).isEqualTo("Sky Air Co");
    }

    // ---- deposit --------------------------------------------------------------------------------------------

    private static final String DEPOSIT_URL = BASE + "/deposit";
    private static final String ONE_DEPOSIT = """
            {"depositNumber": "D-100", "group": "SHORT_ACCOUNT", "depositType": 12, "owner": "INDIVIDUAL",
             "signature": "OWNER_OF_DEPOSIT_AND_SIGNATURE", "depositStatus": "OPEN", "currency": "IRR",
             "personality": "ACTUAL", "availableBalance": 5000000000.50}""";

    @Test
    void depositsAskForTheOpenDepositsOfAnOwnerWhoSignsWithTheCallersSessionAndReadThem() {
        server.expect(requestTo(DEPOSIT_URL)).andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.COOKIE, "JSESSIONID=S1"))
                .andExpect(content().json("""
                        {"cif":"555","depositStatus":"OPEN","needWithdrawableBalance":true,
                         "personality":["ACTUAL"],"signatureStatus":["OWNER_OF_DEPOSIT_AND_SIGNATURE"]}""", true))
                .andRespond(json("{\"result\": {\"deposits\": [" + ONE_DEPOSIT + "]}}"));

        var deposits = client.deposits(new YaghutSession("S1", null), "555");

        assertThat(deposits).containsExactly(new YaghutDeposit("D-100", "SHORT_ACCOUNT", 12L, "INDIVIDUAL",
                "OWNER_OF_DEPOSIT_AND_SIGNATURE", "OPEN", "IRR", "ACTUAL", new java.math.BigDecimal("5000000000.50")));
    }

    @Test
    void depositsAlsoReadAResultThatIsTheListItself() {
        server.expect(requestTo(DEPOSIT_URL)).andRespond(json("{\"result\": [" + ONE_DEPOSIT + "]}"));

        assertThat(client.deposits(new YaghutSession("S1", null), "555")).hasSize(1);
    }

    @Test
    void noDepositsIsAnEmptyListWhateverTheAnswerLooksLike() {
        server.expect(requestTo(DEPOSIT_URL)).andRespond(json("{\"result\": {\"deposits\": []}}"));
        server.expect(requestTo(DEPOSIT_URL)).andRespond(json("{\"result\": {}}"));
        server.expect(requestTo(DEPOSIT_URL)).andRespond(json("{\"result\": null}"));

        var session = new YaghutSession("S1", null);

        assertThat(client.deposits(session, "555")).isEmpty();
        assertThat(client.deposits(session, "555")).isEmpty();
        assertThat(client.deposits(session, "555")).isEmpty();
    }

    @Test
    void aBalanceGivenAsTextIsReadAndOneThatIsNotANumberIsNull() {
        server.expect(requestTo(DEPOSIT_URL)).andRespond(json("""
                {"result": {"deposits": [{"depositNumber": "A", "availableBalance": "1200"},
                 {"depositNumber": "B", "availableBalance": "n/a"}, {"depositNumber": "C", "depositType": "x"}]}}"""));

        var deposits = client.deposits(new YaghutSession("S1", null), "555");

        assertThat(deposits).extracting(YaghutDeposit::availableBalance)
                .containsExactly(new java.math.BigDecimal("1200"), null, null);
        assertThat(deposits.get(2).typeCode()).isNull();
    }

    @Test
    void anEndedSessionAndAnUnknownCustomerAreFaultsOfTheDepositCall() {
        server.expect(requestTo(DEPOSIT_URL)).andRespond(fault(HttpStatus.OK, "InvalidSessionWSException", "ended"));
        server.expect(requestTo(DEPOSIT_URL)).andRespond(fault(HttpStatus.OK, "InvalidCustomerWSException", "none"));

        assertThatThrownBy(() -> client.deposits(new YaghutSession("S1", null), "555"))
                .isInstanceOfSatisfying(YaghutException.class,
                        e -> assertThat(e.fault()).isEqualTo(YaghutException.Fault.INVALID_SESSION));
        assertThatThrownBy(() -> client.deposits(new YaghutSession("S1", null), "555"))
                .isInstanceOfSatisfying(YaghutException.class,
                        e -> assertThat(e.fault()).isEqualTo(YaghutException.Fault.INVALID_CUSTOMER));
    }

    @Test
    void missingCustomerFieldsAreNull() {
        server.expect(requestTo(CUSTOMER_URL)).andRespond(json("{\"result\": {\"code\": \"1\"}}"));

        YaghutCustomer customer = client.customerDetailInfo(new YaghutSession("S1", null), "1");

        assertThat(customer.birthDate()).isNull();
        assertThat(customer.mobile()).isNull();
    }

    @Test
    void anUnknownCustomerIsAFault() {
        server.expect(requestTo(CUSTOMER_URL)).andRespond(fault(HttpStatus.OK, "InvalidCustomerWSException", "none"));

        assertThatThrownBy(() -> client.customerDetailInfo(new YaghutSession("S1", null), "1"))
                .isInstanceOfSatisfying(YaghutException.class,
                        e -> assertThat(e.fault()).isEqualTo(YaghutException.Fault.INVALID_CUSTOMER));
    }

    @Test
    void anEndedSessionIsAFault() {
        server.expect(requestTo(CUSTOMER_URL)).andRespond(fault(HttpStatus.OK, "InvalidSessionWSException", "gone"));

        assertThatThrownBy(() -> client.customerDetailInfo(new YaghutSession("S1", null), "1"))
                .isInstanceOfSatisfying(YaghutException.class,
                        e -> assertThat(e.fault()).isEqualTo(YaghutException.Fault.INVALID_SESSION));
    }

    @Test
    void anUnreadableBirthDateIsTechnical() {
        server.expect(requestTo(CUSTOMER_URL))
                .andRespond(json("{\"result\": {\"birthDate\": \"soon\"}}"));

        assertThatThrownBy(() -> client.customerDetailInfo(new YaghutSession("S1", null), "1"))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    // ---- cookie reading ----------------------------------------------------------------------------------------

    @Test
    void jsessionidIsFoundInAnyHeaderAndTrimmed() {
        assertThat(YaghutClient.jsessionId(List.of("a=b", " JSESSIONID= XYZ ; Path=/"))).isEqualTo("XYZ");
        assertThat(YaghutClient.jsessionId(List.of("a=b", "xJSESSIONID=1"))).isNull();
        assertThat(YaghutClient.jsessionId(List.of())).isNull();
    }
}
