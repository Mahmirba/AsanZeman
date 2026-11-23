import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The gateway token and the nine inquiry services, as the bank's documents show them. Test data only; the national
 * code picks the answer (all of them pass checksum, so a request can be registered for each):
 *
 * <pre>
 * 1000000011  everything passes (a man, born 1369/02/27)        1000000079  a woman: Sepad is skipped
 * 1000000028  Sabte Ahval: deceased                            1000000036  Sabte Ahval: no match (also a minor)
 * 1000000044  Shahkar: the mobile does not match                1000000125  Sepad: no answer in time once, then passes
 * 1000000141  Sepad: not allowed                               1000000214  on the blacklist
 * 1000000281  a bounced cheque                                 1000000354  a non-current loan
 * 1000000427  a credit limit is broken                         1000000494  an import commitment is not allowed
 * 1000000567  import: no commitments listed (no usable answer)  1000000631  an export commitment is not created
 * 1000000702  an exporter, all fine                            anyone else: not an exporter (404), the rest pass
 * </pre>
 *
 * Every inquiry answers 401 without {@code Authorization: Bearer mock-gw-...}, as the real gateway does.
 */
class FakeInquiries {

    private static final String BASE = "/inquiry";
    private static final AtomicInteger TOKENS = new AtomicInteger();
    private static final Map<String, AtomicInteger> CALLS = new ConcurrentHashMap<>();

    static void register(HttpServer server) {
        server.createContext("/gateway/connect/token", FakeInquiries::token);
        inquiry(server, "/ApiContainer.SBCivil.SabtREST/api/Civil/GetEstelamSabt", "nationalCode",
                FakeInquiries::sabt);
        inquiry(server, "/ApiContainer.SBShahkar/api/ShahkarWrap/ServiceIdMatching", "identificationNo",
                (code, body) -> "1000000044".equals(code)
                        ? "{\"response\":600,\"id\":\"200\",\"result\":\"NotIdentifiedException\"}"
                        : "{\"response\":200,\"requestId\":\"1\",\"id\":\"200\",\"result\":\"OK.\"}");
        inquiry(server, "/ApiContainer.SBSepad.RCL1/api/Sepad/GetEstelam", "nationalId", FakeInquiries::sepad);
        inquiry(server, "/ApiContainer.SBCustomerGroup/api/SBGetCstBlackList/GetCstBlackList", "nationalKey",
                (code, body) -> "{\"result\":" + "1000000214".equals(code) + "}");
        inquiry(server, "/ApiContainer.SBSamaCheque/api/CbiSamaCheque/GetBouncedChequesByPersonInfo", "nationalId",
                (code, body) -> "1000000281".equals(code)
                        ? "{\"cheques\":[{\"amount\":200000000,\"serial\":\"0000877508      \"}],\"errors\":null,"
                                + "\"isValid\":true}"
                        : "{\"cheques\":[],\"errors\":null,\"isValid\":true}");
        inquiry(server, "/ApiContainer.SBSamatWrp/api/CbiSamat/GetEstelamAsli2", "cstmrShenase",
                (code, body) -> "{\"returnValue\":{\"estelamAsliRows\":[{\"amSarResid\":"
                        + ("1000000354".equals(code) ? "5000000.00000" : "0") + ",\"amMoavagh\":0,\"amMashkuk\":0,"
                        + "\"amSukht\":0}]},\"hasError\":false,\"errors\":null}");
        inquiry(server, "/ApiContainer.SBCBIBoardRelation/api/SBQueryLimit/QueryLimit", "nationalIdOrLegalId",
                (code, body) -> "1000000427".equals(code)
                        ? "{\"description\":\"limit of one beneficiary group is broken\",\"result\":false}"
                        : "{\"description\":\"no limit is broken\",\"result\":true}");
        inquiry(server, "/ApiContainer.SBRafTaahodArzi.RCL1/api/RafeTaahodArzi/GetTaahodArziRafNashodeh",
                "shenasehMelliOrShomarehKartBazargani", FakeInquiries::imports);
        server.createContext(BASE + "/ApiContainer.SBICEPbn/api/Status/", FakeInquiries::exports);
    }

    private static void token(HttpExchange x) throws IOException {
        FakeServices.read(x);
        FakeServices.reply(x, 200, "{\"access_token\":\"mock-gw-" + TOKENS.incrementAndGet()
                + "\",\"expires_in\":7200,\"token_type\":\"Bearer\",\"scope\":\"all\"}", null);
    }

    /** One inquiry: checks the bearer token, finds the national code in the body, and lets {@code answer} reply. */
    private static void inquiry(HttpServer server, String path, String codeField,
            java.util.function.BiFunction<String, String, String> answer) {
        server.createContext(BASE + path, x -> {
            String body = FakeServices.read(x);
            if (!authorised(x)) {
                FakeServices.reply(x, 401, "{\"status\":401,\"detail\":\"no token\"}", null);
                return;
            }
            String code = FakeServices.field(body, codeField);
            System.out.println("inquiry " + path.substring(path.lastIndexOf('/') + 1) + " for " + code);
            FakeServices.reply(x, 200, answer.apply(code, body), null);
        });
    }

    private static boolean authorised(HttpExchange x) {
        String auth = x.getRequestHeaders().getFirst("Authorization");
        return auth != null && auth.startsWith("Bearer mock-gw-");
    }

    /** The number of Jalali birth date digits the caller sent, echoed back as the registry's own. */
    private static String sabt(String code, String body) {
        String born = FakeServices.field(body, "birthDate");
        String status = "200";
        int alive = 0;
        if ("1000000036".equals(code)) {
            status = "300";
        } else if ("1000000028".equals(code)) {
            alive = 1;
        }
        if ("300".equals(status)) {
            return "{\"estelam3TPersonInfo\":null,\"serviceStatus\":{\"statusField\":\"300\","
                    + "\"messageField\":\"err.record.not.found\"},\"hasError\":false,\"errorList\":null}";
        }
        int gender = "1000000079".equals(code) ? 0 : 1;
        return "{\"estelam3TPersonInfo\":[{\"birthDateField\":" + born + ",\"genderField\":" + gender
                + ",\"isaliveCode\":" + alive + "}],\"serviceStatus\":{\"statusField\":\"200\","
                + "\"messageField\":\"single.record\"},\"hasError\":false,\"errorList\":null}";
    }

    /** 1000000125 does not answer in time on its first call (the client then repeats it), and passes on the next. */
    private static String sepad(String code, String body) {
        if ("1000000125".equals(code) && CALLS.computeIfAbsent(code, k -> new AtomicInteger()).getAndIncrement() == 0) {
            try {
                Thread.sleep(15_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return "1000000141".equals(code)
                ? "{\"payload\":{\"result\":\"0\",\"description\":\"غيرمجاز\",\"errorCode\":null,"
                        + "\"errorMessage\":null},\"errorResult\":null,\"conversationId\":\"c\"}"
                : "{\"payload\":{\"result\":\"1\",\"description\":\"مجاز\",\"errorCode\":null,"
                        + "\"errorMessage\":null},\"errorResult\":null,\"conversationId\":\"c\"}";
    }

    private static String imports(String code, String body) {
        if ("1000000567".equals(code)) {
            return "{\"returnData\":[]}";
        }
        return "{\"returnData\":[{\"darsadTaahodArziRafNashodeh\":\"0\",\"mablagh\":\"0\",\"izMojaz\":"
                + !"1000000494".equals(code) + "}]}";
    }

    private static void exports(HttpExchange x) throws IOException {
        FakeServices.read(x);
        if (!authorised(x)) {
            FakeServices.reply(x, 401, "{\"status\":401,\"detail\":\"no token\"}", null);
            return;
        }
        String path = x.getRequestURI().getPath();
        String code = path.substring(path.lastIndexOf('/') + 1);
        System.out.println("inquiry Status for " + code);
        if ("1000000631".equals(code) || "1000000702".equals(code)) {
            FakeServices.reply(x, 200, "{\"national_id\":\"" + code + "\",\"commitment_percentage\":81.54,"
                    + "\"granting_facility_types\":true,\"commitment_creation\":" + "1000000702".equals(code)
                    + ",\"operations_related_to_currency_allocation\":true}", null);
        } else {
            FakeServices.reply(x, 404, "{\"status\":404,\"detail\":\"No MerchantCommitment matches the given query.\"}",
                    null);
        }
    }
}
