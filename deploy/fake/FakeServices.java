import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fake Yaghut and Notifier for trying the agency panel locally. Test data only.
 *
 * Run:  java deploy/fake/FakeServices.java        (port 8089, or give another port as the argument)
 *
 * Yaghut   POST /yaghut/user/loginStatic   any user logs in as agency cif 9001; password "wrong" is refused
 *          POST /yaghut/branches           two branches
 *          POST /yaghut/customerDetailInfo 036 minor, 044 company, any other 10000000xx an adult, others no
 * Notifier POST /notifier/api/v1/sms/SendSimpleSms   prints the SMS and answers with an smsId
 *          POST /notifier/api/v1/Sms/SendOtp          prints the code (always 1234) and answers with an sId
 *          POST /notifier/api/v1/Sms/OtpValidate      1234 is valid, anything else is 410
 * A Yaghut user name that is a 10-digit national code logs in as that customer.
 *
 * Gateway  POST /gateway/connect/token     a bearer token; every inquiry answers 401 without it
 * Inquiries under /inquiry, the paths of the bank's services; the national code picks the answer (FakeInquiries)
 */
public class FakeServices {

    private static final AtomicInteger SMS_ID = new AtomicInteger(1000);

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8089;
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        server.createContext("/yaghut/user/loginStatic", FakeServices::login);
        server.createContext("/yaghut/branches", FakeServices::branches);
        server.createContext("/yaghut/customerDetailInfo", FakeServices::customer);
        server.createContext("/yaghut/deposit", FakeServices::deposits);
        server.createContext("/aaa/AuthorizationSrv.svc", FakeServices::aaa);
        server.createContext("/personnel-info", FakeServices::personnel);
        server.createContext("/notifier/api/v1/sms/SendSimpleSms", FakeServices::sms);
        server.createContext("/notifier/api/v1/Sms/SendOtp", FakeServices::otpSend);
        server.createContext("/notifier/api/v1/Sms/OtpValidate", FakeServices::otpValidate);
        FakeInquiries.register(server);
        // a slow answer (the Sepad persona) must not hold up every other call
        server.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        System.out.println("Fake Yaghut, Notifier, gateway token and the nine inquiries on http://localhost:" + port);
    }

    private static void login(HttpExchange x) throws IOException {
        String body = read(x);
        if ("wrong".equals(field(body, "password"))) {
            reply(x, 200, "{\"success\":false,\"exception\":\"InvalidCredentialsWSException\","
                    + "\"exception_message\":\"invalid credentials\"}", null);
            return;
        }
        String end = ZonedDateTime.now(ZoneId.of("Asia/Tehran")).plusHours(8)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")) + "IRST";
        String user = field(body, "username");
        // a user name that is a 10-digit national code logs in as that customer, anything else as the agency
        String who = user != null && user.matches("\\d{10}")
                ? "\"name\":\"Ali Rezaei\",\"code\":\"" + user + "\""
                : "\"name\":\"Sky Travel\",\"cif\":\"9001\",\"code\":\"9001\"";
        reply(x, 200, "{\"success\":true,\"result\":{" + who + ",\"sessionExpirationDate\":\"" + end + "\"}}",
                "JSESSIONID=fake-" + System.nanoTime() + "; Path=/");
    }

    private static void branches(HttpExchange x) throws IOException {
        read(x);
        reply(x, 200, "{\"success\":true,\"result\":{\"totalRecord\":2,\"branches\":["
                + "{\"branchCode\":\"0123\",\"name\":\"Valiasr\",\"city\":\"Tehran\"},"
                + "{\"branchCode\":\"0456\",\"name\":\"Saadi\",\"city\":\"Shiraz\"}]}}", null);
    }

    private static void customer(HttpExchange x) throws IOException {
        String code = field(read(x), "code");
        String born = "1990-05-17";
        String type = "ACTUAL";
        if ("1000000036".equals(code)) {
            born = "2015-03-02";
        } else if ("1000000044".equals(code)) {
            type = "LEGAL";
        } else if (code == null || !code.matches("1000000\\d{3}")) {
            reply(x, 200, "{\"success\":false,\"exception\":\"InvalidCustomerWSException\","
                    + "\"exception_message\":\"not a customer\"}", null);
            return;
        }
        reply(x, 200, "{\"success\":true,\"result\":{\"cif\":\"555" + code.substring(8) + "\",\"code\":\"" + code
                + "\",\"firstName\":\"Ali\",\"lastName\":\"Rezaei\","
                + "\"fatherName\":\"Reza\",\"birthDate\":\"" + born + "\",\"mobile\":\"09121234567\","
                + "\"personalityType\":\"" + type + "\",\"gender\":\"MALE\"}}", null);
    }

    /** The bank's role service (SOAP): a user name with "admin" in it is an Admin, anyone else a Branch user. */
    private static void aaa(HttpExchange x) throws IOException {
        String request = read(x);
        String ns = "http://www.sb24.com/Services/Security/AuthorizationSrv";
        String role = request.contains("admin") ? "Admin" : "Branch";
        byte[] body = ("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>"
                + "<GetRoleWindowsResponse xmlns=\"" + ns + "\"><GetRoleWindowsResult>"
                + "<DetailError xmlns=\"" + ns + "/RoleDataResult\"/>"
                + "<UserRoles xmlns=\"" + ns + "/RoleDataResult\" "
                + "xmlns:a=\"http://schemas.microsoft.com/2003/10/Serialization/Arrays\"><a:string>" + role
                + "</a:string></UserRoles></GetRoleWindowsResult></GetRoleWindowsResponse></s:Body></s:Envelope>")
                .getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "text/xml;charset=UTF-8");
        x.sendResponseHeaders(200, body.length);
        x.getResponseBody().write(body);
        x.close();
    }

    /** The user's branch: 0456 or 0999 when the account name says so, otherwise 0123. */
    private static void personnel(HttpExchange x) throws IOException {
        String account = field(read(x), "accountName");
        String branch = account != null && account.contains("0456") ? "0456"
                : account != null && account.contains("0999") ? "0999" : "0123";
        reply(x, 200, "{\"userInfo\":{\"abrnchcod\":\"" + branch + "\",\"prsnCode\":\"1\",\"personelUserName\":\""
                + account + "\",\"aBranchName\":\"Branch " + branch + "\",\"position\":\"clerk\",\"job\":\"branch\"}}",
                null);
    }

    /** Seven deposits: three can be offered (D-100, D-200 cheap and D-300 expensive); the rest must be filtered out. */
    private static void deposits(HttpExchange x) throws IOException {
        FakeServices.read(x);
        reply(x, 200, "{\"success\":true,\"result\":{\"deposits\":["
                + deposit("D-100", "SHORT_ACCOUNT", 1, "INDIVIDUAL", "OPEN", "IRR", "5000000000")
                + "," + deposit("D-200", "PASANDAZ", 2, "INDIVIDUAL", "OPEN", "IRR", "100000000")
                + "," + deposit("D-300", "LONG_ACCOUNT", 3, "INDIVIDUAL", "OPEN", "IRR", "3000000000")
                + "," + deposit("D-400", "SHORT_ACCOUNT", 1, "JOINT", "OPEN", "IRR", "9000000000")
                + "," + deposit("D-500", "SHORT_ACCOUNT", 1, "INDIVIDUAL", "OPEN", "USD", "9000000000")
                + "," + deposit("D-600", "SHORT_ACCOUNT", 1, "INDIVIDUAL", "NEGATIVE_BLOCK", "IRR", "9000000000")
                + "," + deposit("D-700", "ANDOKHTE_ACCOUNT", 9, "INDIVIDUAL", "OPEN", "IRR", "9000000000")
                + "]}}", null);
    }

    private static String deposit(String number, String group, int type, String owner, String status, String currency,
            String balance) {
        return "{\"depositNumber\":\"" + number + "\",\"group\":\"" + group + "\",\"depositType\":" + type
                + ",\"owner\":\"" + owner + "\",\"signature\":\"OWNER_OF_DEPOSIT_AND_SIGNATURE\",\"depositStatus\":\""
                + status + "\",\"currency\":\"" + currency + "\",\"personality\":\"ACTUAL\",\"availableBalance\":"
                + balance + ",\"balance\":" + balance + "}";
    }

    private static void sms(HttpExchange x) throws IOException {
        String body = read(x);
        System.out.println("SMS to " + body.replaceAll("(?s).*\"numbers\"\\s*:\\s*\\[\\s*\"([^\"]*)\".*", "$1")
                + ": " + field(body, "message"));
        reply(x, 200, "{\"retval\":[{\"smsId\":\"" + SMS_ID.incrementAndGet() + "\"}]}", null);
    }

    private static void otpSend(HttpExchange x) throws IOException {
        String number = field(read(x), "number");
        System.out.println("OTP to " + number + ": 1234");
        reply(x, 200, "{\"retval\":[{\"number\":\"" + number + "\",\"smsId\":\"" + SMS_ID.incrementAndGet()
                + "\",\"sId\":\"otp-" + SMS_ID.incrementAndGet() + "\"}]}", null);
    }

    private static void otpValidate(HttpExchange x) throws IOException {
        boolean ok = "1234".equals(field(read(x), "OtpCode"));
        reply(x, ok ? 200 : 410, ok ? "{\"isValid\":true}" : "{\"isValid\":false}", null);
    }

    static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    static String read(HttpExchange x) throws IOException {
        return new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    static void reply(HttpExchange x, int status, String json, String cookie) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        if (cookie != null) {
            x.getResponseHeaders().add("Set-Cookie", cookie);
        }
        x.sendResponseHeaders(status, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }
}
