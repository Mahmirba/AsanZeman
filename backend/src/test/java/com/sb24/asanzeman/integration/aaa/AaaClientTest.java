package com.sb24.asanzeman.integration.aaa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.ws.test.client.RequestMatcher;
import org.springframework.ws.test.client.RequestMatchers;
import org.springframework.ws.test.client.ResponseCreators;
import org.springframework.xml.transform.StringSource;

/** Runs the real SOAP stack (SAAJ, JAXB, WSS4J) against a mock AAA server. */
class AaaClientTest {

    private static final String NS = "http://www.sb24.com/Services/Security/AuthorizationSrv";
    private static final String RESULT_NS = NS + "/RoleDataResult";
    private static final String ERRORS_NS = "http://www.sb24.com/Services/Common/CustomEntities/ResultErrors";
    private static final String ARRAYS_NS = "http://schemas.microsoft.com/2003/10/Serialization/Arrays";
    private static final Map<String, String> NAMESPACES = Map.of("tns", NS, "sp", NS + "/StorageInfoParam",
            "up", NS + "/UserParam");

    private static WebServiceTemplate template(boolean timestamp) {
        return AaaWebServiceTemplates.create("https://aaa.test/AuthorizationSrv.svc", "tech-user", "tech-pass",
                timestamp, HttpClient.newHttpClient(), Duration.ofSeconds(10));
    }

    private static RequestMatcher raw(Consumer<String> assertions) {
        return (uri, request) -> {
            var out = new ByteArrayOutputStream();
            request.writeTo(out);
            assertions.accept(out.toString(StandardCharsets.UTF_8));
        };
    }

    private static StringSource response(String resultContent) {
        return new StringSource("<GetRoleWindowsResponse xmlns=\"" + NS + "\">" + resultContent
                + "</GetRoleWindowsResponse>");
    }

    private static String result(String detailError, String userRoles) {
        return "<GetRoleWindowsResult>" + detailError + userRoles + "</GetRoleWindowsResult>";
    }

    private static String roles(String... names) {
        StringBuilder sb = new StringBuilder("<UserRoles xmlns=\"" + RESULT_NS + "\" xmlns:a=\"" + ARRAYS_NS + "\">");
        for (String name : names) {
            sb.append("<a:string>").append(name).append("</a:string>");
        }
        return sb.append("</UserRoles>").toString();
    }

    private static String noErrors() {
        return "<DetailError xmlns=\"" + RESULT_NS + "\"/>";
    }

    private AaaClient client(WebServiceTemplate template) {
        return new AaaClient(template, "AZS-APP", "AZS-STORE");
    }

    @Test
    void sendsTheNamesAndTheTypedCredentialsAndReadsTheRoles() {
        WebServiceTemplate template = template(false);
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(RequestMatchers.xpath("//tns:storageparam/sp:ApplicationName", NAMESPACES)
                        .evaluatesTo("AZS-APP"))
                .andExpect(RequestMatchers.xpath("//tns:storageparam/sp:StoreName", NAMESPACES)
                        .evaluatesTo("AZS-STORE"))
                .andExpect(RequestMatchers.xpath("//tns:param/up:UserName", NAMESPACES).evaluatesTo("ali@sb24.ir"))
                .andExpect(RequestMatchers.xpath("//tns:param/up:Password", NAMESPACES).evaluatesTo("typed-pw"))
                .andRespond(ResponseCreators.withPayload(response(result(noErrors(), roles("Branch", "Other")))));

        AaaRoleResult result = client(template).getRoleWindows("ali@sb24.ir", "typed-pw");

        assertThat(result.errors()).isEmpty();
        assertThat(result.roles()).containsExactly("Branch", "Other");
        server.verify();
    }

    @Test
    void addsTheTechnicalAccountUsernameTokenAsPlainTextWithoutTimestampNonceOrCreated() {
        WebServiceTemplate template = template(false);
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(raw(xml -> {
            assertThat(xml).contains("UsernameToken").contains("tech-user").contains("tech-pass")
                    .contains("#PasswordText");
            assertThat(xml).doesNotContain("Timestamp").doesNotContain("Nonce").doesNotContain("Created");
            // mustUnderstand is 0 (6.2): absent or false, never 1/true
            assertThat(xml).doesNotContain("mustUnderstand=\"1\"").doesNotContain("mustUnderstand=\"true\"");
        })).andRespond(ResponseCreators.withPayload(response(result(noErrors(), roles("Admin")))));

        client(template).getRoleWindows("u", "p");

        server.verify();
    }

    @Test
    void addsATimestampOnlyWhenConfigured() {
        WebServiceTemplate template = template(true);
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(raw(xml -> assertThat(xml).contains("UsernameToken").contains("Timestamp")))
                .andRespond(ResponseCreators.withPayload(response(result(noErrors(), roles("Admin")))));

        client(template).getRoleWindows("u", "p");

        server.verify();
    }

    @Test
    void sendsTheSoapActionOfGetRoleWindows() {
        WebServiceTemplate template = template(false);
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect((uri, request) -> assertThat(((SoapMessage) request).getSoapAction())
                        .contains("http://www.sb24.com/Services/Security/AuthorizationSrv/GetRoleWindows/Request"))
                .andRespond(ResponseCreators.withPayload(response(result(noErrors(), roles("Admin")))));

        client(template).getRoleWindows("u", "p");

        server.verify();
    }

    @Test
    void readsEveryDetailError() {
        String errors = "<DetailError xmlns=\"" + RESULT_NS + "\" xmlns:e=\"" + ERRORS_NS + "\">"
                + "<e:ResultErrors><e:ErrorCode>101</e:ErrorCode><e:ErrorDesc>bad password</e:ErrorDesc>"
                + "<e:InnerException>ex1</e:InnerException></e:ResultErrors>"
                + "<e:ResultErrors><e:ErrorDesc xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:nil=\"true\"/></e:ResultErrors></DetailError>";
        WebServiceTemplate template = template(false);
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(RequestMatchers.anything())
                .andRespond(ResponseCreators.withPayload(response(result(errors, roles()))));

        AaaRoleResult result = client(template).getRoleWindows("u", "wrong");

        assertThat(result.errors()).containsExactly(new AaaRoleResult.AaaError(101, "bad password", "ex1"),
                new AaaRoleResult.AaaError(0, null, null));
        assertThat(result.roles()).isEmpty();
    }

    @Test
    void aNilOrMissingRoleListIsEmpty() {
        String nilRoles = "<UserRoles xmlns=\"" + RESULT_NS + "\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:nil=\"true\"/>";
        WebServiceTemplate template = template(false);
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(RequestMatchers.anything()).andRespond(ResponseCreators.withPayload(response(
                result(noErrors(), nilRoles))));
        server.expect(RequestMatchers.anything()).andRespond(ResponseCreators.withPayload(response(
                "<GetRoleWindowsResult/>")));

        assertThat(client(template).getRoleWindows("u", "p").roles()).isEmpty();
        assertThat(client(template).getRoleWindows("u", "p").roles()).isEmpty();
    }

    @Test
    void aSoapFaultIsTechnical() {
        WebServiceTemplate template = template(false);
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(RequestMatchers.anything())
                .andRespond(ResponseCreators.withServerOrReceiverFault("boom", Locale.ENGLISH));

        assertThatThrownBy(() -> client(template).getRoleWindows("u", "p")).isInstanceOfSatisfying(
                TemporarilyUnavailableException.class, ex -> assertThat(ex.service()).isEqualTo("AAA"));
    }

    @Test
    void aConnectionErrorIsTechnical() {
        WebServiceTemplate template = template(false);
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(RequestMatchers.anything()).andRespond(ResponseCreators.withException(new IOException("timed out")));

        assertThatThrownBy(() -> client(template).getRoleWindows("u", "p"))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void anAnswerWithoutAResultIsTechnical() {
        WebServiceTemplate template = template(false);
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(RequestMatchers.anything()).andRespond(ResponseCreators.withPayload(response("")));

        assertThatThrownBy(() -> client(template).getRoleWindows("u", "p"))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void aNilResultIsTechnical() {
        WebServiceTemplate template = template(false);
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(RequestMatchers.anything()).andRespond(ResponseCreators.withPayload(response(
                "<GetRoleWindowsResult xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:nil=\"true\"/>")));

        assertThatThrownBy(() -> client(template).getRoleWindows("u", "p"))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }

    @Test
    void anUnreadableAnswerIsTechnical() {
        WebServiceTemplate template = template(false);
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        server.expect(RequestMatchers.anything())
                .andRespond(ResponseCreators.withPayload(new StringSource("<Unexpected xmlns=\"urn:other\"/>")));

        assertThatThrownBy(() -> client(template).getRoleWindows("u", "p"))
                .isInstanceOf(TemporarilyUnavailableException.class);
    }
}
