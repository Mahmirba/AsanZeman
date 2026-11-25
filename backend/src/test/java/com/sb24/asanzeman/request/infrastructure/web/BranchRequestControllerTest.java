package com.sb24.asanzeman.request.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sb24.asanzeman.request.domain.model.BranchRequestDetails;
import com.sb24.asanzeman.request.domain.model.BranchRequestRow;
import com.sb24.asanzeman.request.domain.model.BranchSearchCriteria;
import com.sb24.asanzeman.request.domain.model.BranchUser;
import com.sb24.asanzeman.request.domain.model.CollateralAllocation;
import com.sb24.asanzeman.request.domain.model.CollateralKind;
import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.NotYourBranchException;
import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.PriceClass;
import com.sb24.asanzeman.request.domain.model.RejectionReason;
import com.sb24.asanzeman.request.domain.model.RequestLockedByOtherException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.model.ReviewLock;
import com.sb24.asanzeman.request.domain.port.in.BranchCommand;
import com.sb24.asanzeman.request.domain.port.in.BranchQueryUseCase;
import com.sb24.asanzeman.request.domain.port.in.BranchReviewUseCase;
import com.sb24.asanzeman.request.domain.port.in.ReviewResult;
import com.sb24.asanzeman.shared.model.Rials;
import com.sb24.asanzeman.shared.web.ProblemDetailExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BranchRequestControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();
    private static final BranchUser ALI = new BranchUser(USER, "ali", "0123", false);

    private BranchQueryUseCase queries;
    private BranchReviewUseCase reviews;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        queries = mock(BranchQueryUseCase.class);
        reviews = mock(BranchReviewUseCase.class);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new BranchRequestController(queries, reviews, messages))
                .setControllerAdvice(new ProblemDetailExceptionHandler(messages))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        login("BRANCH");
    }

    private static void login(String role) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("ali").claim("uid", USER.toString())
                .claim("role", role).claim("branch_code", "0123").issuedAt(NOW).expiresAt(NOW.plusSeconds(900)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static BranchRequestRow row(String lockedBy) {
        return new BranchRequestRow(new RequestSummary(REQUEST, "012300420509020001", RequestType.ISSUANCE,
                RequestStatus.UNDER_REVIEW, "1000000011", "555", "Ali Rezaei", 1_000_000_000L, "0123", "Valiasr",
                LocalDate.of(2027, 3, 11), NOW, "Sky Travel", "09121234567"), lockedBy, lockedBy == null ? null : NOW,
                "0456", null, null, null);
    }

    // ---- reading --------------------------------------------------------------------------------------------

    @Test
    void theReportStreamsACsvFileWithTheBothBranchesTheGuaranteeNumberAndTheRejection() throws Exception {
        Mockito.doAnswer(call -> {
            Consumer<List<BranchRequestRow>> sink = call.getArgument(2);
            sink.accept(List.of(row("sara")));
            return null;
        }).when(queries).export(eq(ALI), any(), any());

        var started = mvc.perform(get("/api/branch/requests/export").param("status", "ISSUED"))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andReturn();
        var result = mvc.perform(MockMvcRequestBuilders
                .asyncDispatch(started)).andExpect(status().isOk()).andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("attachment").contains("report.csv");
        String csv = new String(result.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿کد ملی مشتری,").contains("شعبه رسیدگی‌کننده")
                .contains("1000000011,555,Ali Rezaei,1000000000,0123,Valiasr,0456,");
    }

    @Test
    void aBadReportFilterIsA400BeforeAnythingIsStreamed() throws Exception {
        mvc.perform(get("/api/branch/requests/export").param("status", "NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void theInboxShowsEachRequestWithItsHolderAndWhetherTheUserMayActOnIt() throws Exception {
        when(queries.search(eq(ALI), any(), eq(0), eq(20))).thenReturn(new PageResult<>(List.of(row("sara")), 1, 0, 20));

        mvc.perform(get("/api/branch/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].request.trackingCode").value("012300420509020001"))
                .andExpect(jsonPath("$.items[0].request.statusLabel").value("در حال بررسی"))
                .andExpect(jsonPath("$.items[0].lockedBy").value("sara"))
                .andExpect(jsonPath("$.items[0].canAct").value(true))
                .andExpect(jsonPath("$.items[0].handlingBranch").value("0456"));
    }

    @Test
    void theFiltersAreTurnedIntoASearchWithTheJalaliDatesAsStartAndEndOfDay() throws Exception {
        when(queries.search(any(), any(), eq(2), eq(5))).thenReturn(new PageResult<>(List.of(), 0, 2, 5));

        mvc.perform(get("/api/branch/requests").param("status", "ISSUED", "rejected").param("nationalCode", "1000000011")
                        .param("customerNumber", "555").param("trackingCode", "T1")
                        .param("registeredFrom", "1405/09/01").param("registeredTo", "1405/09/02")
                        .param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        var criteria = ArgumentCaptor.forClass(BranchSearchCriteria.class);
        verify(queries).search(eq(ALI), criteria.capture(), eq(2), eq(5));
        assertThat(criteria.getValue().statuses()).containsExactlyInAnyOrder(RequestStatus.ISSUED,
                RequestStatus.REJECTED);
        assertThat(criteria.getValue().customerCode()).isEqualTo("1000000011");
        assertThat(criteria.getValue().customerCif()).isEqualTo("555");
        assertThat(criteria.getValue().trackingCode()).isEqualTo("T1");
        assertThat(criteria.getValue().registeredFrom()).isEqualTo(Instant.parse("2026-11-21T20:30:00Z"));
        assertThat(criteria.getValue().registeredTo()).isEqualTo(Instant.parse("2026-11-23T20:30:00Z"));
    }

    @Test
    void aBadFilterIs400() throws Exception {
        mvc.perform(get("/api/branch/requests").param("status", "NONSENSE")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/branch/requests").param("nationalCode", "123")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/branch/requests").param("registeredFrom", "not a date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theDetailsCarryTheCompanionsAndTheCollateral() throws Exception {
        var companion = new Companion("Sara Rezaei", "0012345679", LocalDate.of(2016, 4, 1), "P1");
        var allocation = new CollateralAllocation("D-100", CollateralKind.CASH, PriceClass.CHEAP, 20, BigDecimal.ONE,
                Rials.of(200_000_000L), Rials.of(200_000_000L));
        when(queries.get(ALI, REQUEST)).thenReturn(new BranchRequestDetails(row(null), List.of(companion),
                List.of(allocation)));

        mvc.perform(get("/api/branch/requests/" + REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.lockedBy").doesNotExist())
                .andExpect(jsonPath("$.companions[0].fullName").value("Sara Rezaei"))
                .andExpect(jsonPath("$.companions[0].birthDate").value("1395/01/13"))
                .andExpect(jsonPath("$.collateral[0].depositNumber").value("D-100"))
                .andExpect(jsonPath("$.collateral[0].collateral").value(200_000_000L));
    }

    @Test
    void theRevocationReasonsAreListedWhenAskedFor() throws Exception {
        UUID id = UUID.randomUUID();
        when(queries.rejectionReasons("REVOCATION")).thenReturn(List.of(new RejectionReason(id, "Still needed")));

        mvc.perform(get("/api/branch/rejection-reasons").param("kind", "revocation")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("Still needed"));
    }

    @Test
    void theRejectionReasonsAreListed() throws Exception {
        UUID id = UUID.randomUUID();
        when(queries.rejectionReasons("ISSUANCE")).thenReturn(List.of(new RejectionReason(id, "Documents are incomplete")));

        mvc.perform(get("/api/branch/rejection-reasons")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].text").value("Documents are incomplete"));
    }

    // ---- acting ---------------------------------------------------------------------------------------------

    private static ReviewResult held() {
        return new ReviewResult(REQUEST, RequestStatus.UNDER_REVIEW, "ali", NOW);
    }

    private BranchCommand sent(String ip) {
        var command = ArgumentCaptor.forClass(BranchCommand.class);
        verify(reviews, Mockito.atLeastOnce()).take(command.capture());
        assertThat(command.getValue().clientIp()).isEqualTo(ip);
        return command.getValue();
    }

    @Test
    void takingARequestPassesTheUserFromTheTokenAndWhereTheCallCameFrom() throws Exception {
        when(reviews.take(any())).thenReturn(held());

        mvc.perform(post("/api/branch/requests/" + REQUEST + "/lock").header("User-Agent", "Firefox")
                        .with(request -> {
                            request.setRemoteAddr("10.0.0.5");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.statusLabel").value("در حال بررسی"))
                .andExpect(jsonPath("$.heldBy").value("ali"));

        BranchCommand command = sent("10.0.0.5");
        assertThat(command.user()).isEqualTo(ALI);
        assertThat(command.requestId()).isEqualTo(REQUEST);
        assertThat(command.userAgent()).isEqualTo("Firefox");
    }

    @Test
    void anAdminTokenMakesAnAdminUser() throws Exception {
        login("ADMIN");
        when(reviews.take(any())).thenReturn(held());

        mvc.perform(post("/api/branch/requests/" + REQUEST + "/lock")).andExpect(status().isOk());

        var command = ArgumentCaptor.forClass(BranchCommand.class);
        verify(reviews).take(command.capture());
        assertThat(command.getValue().user().admin()).isTrue();
    }

    @Test
    void approveAbandonAndTheOthersCallTheirUseCases() throws Exception {
        UUID reason = UUID.randomUUID();
        when(reviews.approve(any())).thenReturn(held());
        when(reviews.abandon(any())).thenReturn(held());
        when(reviews.reject(any(), eq(reason), eq("missing page"))).thenReturn(held());
        when(reviews.issue(any(), eq("GT-1"))).thenReturn(held());
        when(reviews.forceRelease(any(), eq("on leave"))).thenReturn(held());
        when(reviews.completeRevocation(any())).thenReturn(held());

        mvc.perform(post("/api/branch/requests/" + REQUEST + "/approve")).andExpect(status().isOk());
        mvc.perform(post("/api/branch/requests/" + REQUEST + "/abandon")).andExpect(status().isOk());
        mvc.perform(post("/api/branch/requests/" + REQUEST + "/complete-revocation")).andExpect(status().isOk());
        mvc.perform(post("/api/branch/requests/" + REQUEST + "/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reasonId\":\"" + reason + "\",\"comment\":\"missing page\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/branch/requests/" + REQUEST + "/issue").contentType(MediaType.APPLICATION_JSON)
                .content("{\"guaranteeNumber\":\"GT-1\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/branch/requests/" + REQUEST + "/force-release").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"on leave\"}")).andExpect(status().isOk());
    }

    @Test
    void theRequiredBodyFieldsAreChecked() throws Exception {
        mvc.perform(post("/api/branch/requests/" + REQUEST + "/reject").contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"x\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.reasonId").exists());
        mvc.perform(post("/api/branch/requests/" + REQUEST + "/issue").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.guaranteeNumber").exists());
        mvc.perform(post("/api/branch/requests/" + REQUEST + "/force-release").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
    }

    @Test
    void theRefusalsHaveTheirStatusAndPersianText() throws Exception {
        when(reviews.take(any())).thenThrow(new NotYourBranchException());
        when(reviews.approve(any())).thenThrow(new RequestLockedByOtherException(
                new ReviewLock(UUID.randomUUID(), "sara", NOW, NOW)));
        when(reviews.abandon(any())).thenThrow(new InvalidRequestDataException("x"));

        mvc.perform(post("/api/branch/requests/" + REQUEST + "/lock")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("request.branch-forbidden"));
        mvc.perform(post("/api/branch/requests/" + REQUEST + "/approve")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("این درخواست در حال اقدام توسط sara است (از 1405/09/02 13:30)."));
        mvc.perform(post("/api/branch/requests/" + REQUEST + "/abandon")).andExpect(status().isBadRequest());
    }

    @Test
    void onlyBranchAndAdminSessionsMayCallItAndOnlyAnAdminMayForceARelease() throws Exception {
        assertThat(BranchRequestController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAnyRole('BRANCH','ADMIN')");
        assertThat(BranchRequestController.class.getMethod("forceRelease", Jwt.class, UUID.class,
                BranchRequestController.ReleaseBody.class, HttpServletRequest.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasRole('ADMIN')");
    }
}
