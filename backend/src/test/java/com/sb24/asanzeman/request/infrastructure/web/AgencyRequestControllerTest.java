package com.sb24.asanzeman.request.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException;
import com.sb24.asanzeman.request.domain.model.CustomerUnderageException;
import com.sb24.asanzeman.request.domain.model.DuplicateRegistrationException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.port.in.CompanionInput;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestUseCase;
import com.sb24.asanzeman.request.domain.port.in.GetRequestUseCase;
import com.sb24.asanzeman.request.domain.port.in.RegisterRequestCommand;
import com.sb24.asanzeman.request.domain.port.in.SearchRequestsUseCase;
import com.sb24.asanzeman.request.domain.port.in.RegisterRequestUseCase;
import com.sb24.asanzeman.request.domain.port.in.RegisteredRequest;
import com.sb24.asanzeman.shared.web.ProblemDetailExceptionHandler;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgencyRequestControllerTest {

    private static final UUID AGENCY = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();
    private static final RegisteredRequest REGISTERED = new RegisteredRequest(UUID.randomUUID(),
            "012300420508300001", RequestStatus.REGISTERED_AT_AGENCY);
    private static final String NATIONAL_CODE = "0012345679";

    private RegisterRequestUseCase register;
    private SearchRequestsUseCase search;
    private GetRequestUseCase get;
    private CancelRequestUseCase cancel;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        register = mock(RegisterRequestUseCase.class);
        search = mock(SearchRequestsUseCase.class);
        get = mock(GetRequestUseCase.class);
        cancel = mock(CancelRequestUseCase.class);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new AgencyRequestController(register, search, get, cancel, messages))
                .setControllerAdvice(new ProblemDetailExceptionHandler(messages))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("Sky Travel")
                .claim("uid", AGENCY.toString()).claim("sid", SESSION.toString()).claim("role", "AGENCY")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static String body(String amount, String dueDate, String companions) {
        return "{\"nationalCode\":\"" + NATIONAL_CODE + "\",\"branchCode\":\"0123\",\"amount\":" + amount
                + ",\"dueDate\":\"" + dueDate + "\"" + (companions == null ? "" : ",\"companions\":" + companions)
                + "}";
    }

    private org.springframework.test.web.servlet.ResultActions send(String json) throws Exception {
        return mvc.perform(post("/api/agency/requests").contentType(MediaType.APPLICATION_JSON).content(json)
                .header("User-Agent", "Firefox").with(request -> {
                    request.setRemoteAddr("10.0.0.5");
                    return request;
                }));
    }

    @Test
    void registersAndReturns201WithTheTrackingCode() throws Exception {
        when(register.register(any())).thenReturn(REGISTERED);

        send(body("100000000", "1405/11/01", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingCode").value("012300420508300001"))
                .andExpect(jsonPath("$.status").value("REGISTERED_AT_AGENCY"))
                .andExpect(jsonPath("$.id").value(REGISTERED.id().toString()));
    }

    @Test
    void theAgencyAndSessionComeFromTheTokenAndTheJalaliDueDateIsConverted() throws Exception {
        when(register.register(any())).thenReturn(REGISTERED);

        send(body("100000000", "1405/11/01", null)).andExpect(status().isCreated());

        ArgumentCaptor<RegisterRequestCommand> command = ArgumentCaptor.forClass(RegisterRequestCommand.class);
        verify(register).register(command.capture());
        RegisterRequestCommand c = command.getValue();
        assertThat(c.agencyId()).isEqualTo(AGENCY);
        assertThat(c.sessionId()).isEqualTo(SESSION);
        assertThat(c.registeredBy()).isEqualTo("Sky Travel");
        assertThat(c.nationalCode()).isEqualTo(NATIONAL_CODE);
        assertThat(c.branchCode()).isEqualTo("0123");
        assertThat(c.amount()).isEqualTo(100_000_000L);
        // 1405/11/01 is 21 January 2027
        assertThat(c.dueDate()).isEqualTo(LocalDate.of(2027, 1, 21));
        assertThat(c.companions()).isEmpty();
        assertThat(c.clientIp()).isEqualTo("10.0.0.5");
        assertThat(c.userAgent()).isEqualTo("Firefox");
    }

    @Test
    void companionsAreConverted() throws Exception {
        when(register.register(any())).thenReturn(REGISTERED);
        String companions = "[{\"fullName\":\"Sara Rezaei\",\"nationalCode\":\"" + NATIONAL_CODE
                + "\",\"birthDate\":\"1394/01/01\",\"passportNumber\":\"P1234567\"}]";

        send(body("1000", "1405/11/01", companions)).andExpect(status().isCreated());

        ArgumentCaptor<RegisterRequestCommand> command = ArgumentCaptor.forClass(RegisterRequestCommand.class);
        verify(register).register(command.capture());
        assertThat(command.getValue().companions()).containsExactly(
                new CompanionInput("Sara Rezaei", NATIONAL_CODE, LocalDate.of(2015, 3, 21), "P1234567"));
    }

    @Test
    void aMissingFieldIs400WithTheViolations() throws Exception {
        send("{\"nationalCode\":\"" + NATIONAL_CODE + "\",\"amount\":1000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.violations.branchCode").exists())
                .andExpect(jsonPath("$.violations.dueDate").exists());
    }

    @Test
    void aBadNationalCodeIs400() throws Exception {
        send(body("1000", "1405/11/01", null).replace(NATIONAL_CODE, "1234567890"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.nationalCode").exists());
    }

    @Test
    void anAmountOfZeroOrMoreThanSixteenDigitsIs400() throws Exception {
        send(body("0", "1405/11/01", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.amount").exists());
        send(body("10000000000000000", "1405/11/01", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.amount").exists());
    }

    @Test
    void moreThanFiveCompanionsIs400() throws Exception {
        String one = "{\"fullName\":\"A\",\"nationalCode\":\"" + NATIONAL_CODE
                + "\",\"birthDate\":\"1394/01/01\",\"passportNumber\":\"P1\"}";
        String six = "[" + String.join(",", java.util.Collections.nCopies(6, one)) + "]";

        send(body("1000", "1405/11/01", six)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.companions").exists());
    }

    @Test
    void aDateThatIsNotARealJalaliDateIs400() throws Exception {
        send(body("1000", "1405/13/45", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.invalid-data"));
        send(body("1000", "2027-01-21", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.invalid-data"));
    }

    @Test
    void businessRefusalsKeepTheirStatusAndPersianText() throws Exception {
        doThrow(new CustomerUnderageException()).when(register).register(any());
        send(body("1000", "1405/11/01", null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.customer-underage"))
                .andExpect(jsonPath("$.detail").value(
                        "کاربر گرامی! باتوجه به اینکه سن مشتری کمتر از 18 سال است، امکان ثبت درخواست فراهم نیست."));

        doThrow(new DuplicateRegistrationException()).when(register).register(any());
        send(body("1000", "1405/11/01", null)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("request.duplicate-registration"));

        doThrow(new YaghutSessionExpiredException()).when(register).register(any());
        send(body("1000", "1405/11/01", null)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.yaghut.session-expired"));
    }

    @Test
    void onlyAnAgencySessionMayCallIt() {
        assertThat(AgencyRequestController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('AGENCY')");
    }

    @Test
    void theBodyHasNoCompanionsByDefault() {
        assertThat(new RegisterRequestBody("c", "b", 1, "d", null).companions()).isNull();
        assertThat(List.of()).isEmpty();
    }

    // ---- tracking list, export, details, cancel -------------------------------------------------------------

    private static final com.sb24.asanzeman.request.domain.model.RequestSummary ROW =
            new com.sb24.asanzeman.request.domain.model.RequestSummary(UUID.randomUUID(), "012300420508300001",
                    com.sb24.asanzeman.request.domain.model.RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY,
                    "1000000011", "555", "Ali Rezaei", 250_000_000L, "0123", "Valiasr", LocalDate.of(2027, 1, 21),
                    Instant.parse("2026-11-21T10:00:00Z"), "Sky Travel", "09121234567");

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getRequests(
            String suffix) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/api/agency/requests" + suffix);
    }

    @Test
    void theListIsPagedAndLabelledAndScopedToTheTokenAgency() throws Exception {
        when(search.search(any(), org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.eq(10)))
                .thenReturn(new com.sb24.asanzeman.request.domain.model.PageResult<>(List.of(ROW), 21, 2, 10));

        mvc.perform(getRequests("").param("page", "2").param("size", "10").param("type", "ISSUANCE")
                        .param("name", "Rez").param("nationalCode", NATIONAL_CODE)
                        .param("registeredFrom", "1405/08/01").param("registeredTo", "1405/08/30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(21))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.items[0].trackingCode").value("012300420508300001"))
                .andExpect(jsonPath("$.items[0].status").value("REGISTERED_AT_AGENCY"))
                .andExpect(jsonPath("$.items[0].statusLabel").value("ثبت درخواست در پنل آژانس"))
                .andExpect(jsonPath("$.items[0].typeLabel").value("صدور ضمانت‌نامه"))
                .andExpect(jsonPath("$.items[0].dueDate").value("1405/11/01"))
                .andExpect(jsonPath("$.items[0].registeredAtJalali").value("1405/08/30 13:30:00"))
                .andExpect(jsonPath("$.items[0].branchName").value("Valiasr"));

        ArgumentCaptor<com.sb24.asanzeman.request.domain.model.RequestSearchCriteria> criteria =
                ArgumentCaptor.forClass(com.sb24.asanzeman.request.domain.model.RequestSearchCriteria.class);
        verify(search).search(criteria.capture(), org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.eq(10));
        assertThat(criteria.getValue().agencyId()).isEqualTo(AGENCY);
        assertThat(criteria.getValue().customerCode()).isEqualTo(NATIONAL_CODE);
        assertThat(criteria.getValue().name()).isEqualTo("Rez");
        // 1405/08/01 starts at midnight Tehran (UTC+3:30); 1405/08/30 includes its whole day
        assertThat(criteria.getValue().registeredFrom()).isEqualTo(Instant.parse("2026-10-22T20:30:00Z"));
        assertThat(criteria.getValue().registeredTo()).isEqualTo(Instant.parse("2026-11-21T20:30:00Z"));
    }

    @Test
    void aBadFilterIs400() throws Exception {
        mvc.perform(getRequests("").param("registeredFrom", "2026-11-21")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("request.invalid-data"));
        mvc.perform(getRequests("").param("type", "NOPE")).andExpect(status().isBadRequest());
        mvc.perform(getRequests("").param("nationalCode", "1234567890")).andExpect(status().isBadRequest());
        mvc.perform(getRequests("").param("registeredFrom", "1405/08/30").param("registeredTo", "1405/08/01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theExportStreamsACsvFileWithTheSameFilters() throws Exception {
        org.mockito.Mockito.doAnswer(call -> {
            java.util.function.Consumer<List<com.sb24.asanzeman.request.domain.model.RequestSummary>> sink =
                    call.getArgument(1);
            sink.accept(List.of(ROW));
            return null;
        }).when(search).export(any(), any());

        var started = mvc.perform(getRequests("/export").param("name", "Rez")).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.request().asyncStarted())
                .andReturn();
        var result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .asyncDispatch(started)).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment")
                .contains("requests.csv");
        String csv = new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿کد ملی مشتری,").contains("1000000011,555,Ali Rezaei,250000000,0123,Valiasr");
        verify(search).export(any(), any());
    }

    @Test
    void aBadExportFilterIsA400BeforeAnythingIsStreamed() throws Exception {
        mvc.perform(getRequests("/export").param("registeredFrom", "bad")).andExpect(status().isBadRequest());
    }

    @Test
    void detailsShowTheCompanionsWithJalaliBirthDates() throws Exception {
        var details = new com.sb24.asanzeman.request.domain.model.RequestDetails(ROW,
                List.of(new com.sb24.asanzeman.request.domain.model.Companion("Sara Rezaei", NATIONAL_CODE,
                        LocalDate.of(2015, 3, 21), "P1234567")));
        when(get.get(AGENCY, ROW.id())).thenReturn(details);

        mvc.perform(getRequests("/" + ROW.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.trackingCode").value("012300420508300001"))
                .andExpect(jsonPath("$.companions[0].fullName").value("Sara Rezaei"))
                .andExpect(jsonPath("$.companions[0].birthDate").value("1394/01/01"))
                .andExpect(jsonPath("$.companions[0].passportNumber").value("P1234567"));
    }

    @Test
    void anUnknownRequestIs404WithAPersianProblem() throws Exception {
        doThrow(new com.sb24.asanzeman.request.domain.model.RequestNotFoundException()).when(get)
                .get(any(), any());

        mvc.perform(getRequests("/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("request.not-found"))
                .andExpect(jsonPath("$.detail").value("درخواست یافت نشد."));
    }

    @Test
    void cancelTakesTheAgencyAndNameFromTheTokenAndReportsTheOutcome() throws Exception {
        when(cancel.cancel(any())).thenReturn(new com.sb24.asanzeman.request.domain.port.in.CancelRequestResult(
                ROW.id(), com.sb24.asanzeman.request.domain.model.CancelOutcome.CANCELLED, RequestStatus.CANCELLED));

        mvc.perform(post("/api/agency/requests/" + ROW.id() + "/cancel").header("User-Agent", "Firefox")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.5");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CANCELLED"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(cancel).cancel(new com.sb24.asanzeman.request.domain.port.in.CancelRequestCommand(AGENCY,
                "Sky Travel", ROW.id(), "10.0.0.5", "Firefox"));
    }

    @Test
    void thePersonInTheTokenIsWhoRegistersAndCancelsWhileTheAgencyNameStaysTheSubject() throws Exception {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("Sky Travel").claim("usr", "ali.sky")
                .claim("uid", AGENCY.toString()).claim("sid", SESSION.toString()).claim("role", "AGENCY")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(register.register(any())).thenReturn(REGISTERED);
        when(cancel.cancel(any())).thenReturn(new com.sb24.asanzeman.request.domain.port.in.CancelRequestResult(
                ROW.id(), com.sb24.asanzeman.request.domain.model.CancelOutcome.CANCELLED, RequestStatus.CANCELLED));

        send(body("100000000", "1405/11/01", null)).andExpect(status().isCreated());
        mvc.perform(post("/api/agency/requests/" + ROW.id() + "/cancel")).andExpect(status().isOk());

        ArgumentCaptor<RegisterRequestCommand> registered = ArgumentCaptor.forClass(RegisterRequestCommand.class);
        verify(register).register(registered.capture());
        assertThat(registered.getValue().registeredBy()).isEqualTo("ali.sky");
        ArgumentCaptor<com.sb24.asanzeman.request.domain.port.in.CancelRequestCommand> cancelled =
                ArgumentCaptor.forClass(com.sb24.asanzeman.request.domain.port.in.CancelRequestCommand.class);
        verify(cancel).cancel(cancelled.capture());
        assertThat(cancelled.getValue().agencyName()).isEqualTo("Sky Travel");
        assertThat(cancelled.getValue().actor()).isEqualTo("ali.sky");
    }

    @Test
    void cancelRefusalsAreConflictsWithPersianTexts() throws Exception {
        doThrow(new com.sb24.asanzeman.request.domain.model.RevocationNotAvailableException()).when(cancel)
                .cancel(any());
        mvc.perform(post("/api/agency/requests/" + ROW.id() + "/cancel")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("request.revocation-not-available"));

        doThrow(new com.sb24.asanzeman.request.domain.model.RequestBusyException()).when(cancel).cancel(any());
        mvc.perform(post("/api/agency/requests/" + ROW.id() + "/cancel")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("request.busy"));

        doThrow(new com.sb24.asanzeman.request.domain.model.RequestNotCancellableException()).when(cancel)
                .cancel(any());
        mvc.perform(post("/api/agency/requests/" + ROW.id() + "/cancel")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("request.not-cancellable"));
    }
}
