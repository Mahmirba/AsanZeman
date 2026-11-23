package com.sb24.asanzeman.request.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sb24.asanzeman.inquiry.domain.model.FailureKind;
import com.sb24.asanzeman.inquiry.domain.model.InquiryCooldownException;
import com.sb24.asanzeman.inquiry.domain.model.InquiryOutcome;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRun;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRunNotFoundException;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRunView;
import com.sb24.asanzeman.inquiry.domain.model.InquiryType;
import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict.Decision;
import com.sb24.asanzeman.inquiry.domain.model.RunStatus;
import com.sb24.asanzeman.request.domain.model.RequestNotInquirableException;
import com.sb24.asanzeman.request.domain.port.in.CustomerInquiryUseCase;
import com.sb24.asanzeman.shared.web.ProblemDetailExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CustomerInquiryControllerTest {

    private static final String CUSTOMER = "1000000011";
    private static final UUID REQUEST = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    private CustomerInquiryUseCase inquiries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        inquiries = mock(CustomerInquiryUseCase.class);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new CustomerInquiryController(inquiries, messages))
                .setControllerAdvice(new ProblemDetailExceptionHandler(messages))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(CUSTOMER).claim("role", "CUSTOMER")
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(1800)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static InquiryRun run(RunStatus status, Decision decision) {
        return new InquiryRun(UUID.randomUUID(), REQUEST, status, decision, NOW,
                status == RunStatus.RUNNING ? null : NOW.plusSeconds(8));
    }

    @Test
    void startingARunIsAcceptedAtOnceAndTheRunIsStillRunning() throws Exception {
        var running = run(RunStatus.RUNNING, null);
        when(inquiries.start(CUSTOMER, REQUEST)).thenReturn(new InquiryRunView(running, List.of()));

        mvc.perform(post("/api/customer/requests/" + REQUEST + "/inquiries"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value(running.id().toString()))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.decision").doesNotExist())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.inquiries").isEmpty());
    }

    @Test
    void aPassedRunListsEveryInquiryAndHasNoMessage() throws Exception {
        var done = run(RunStatus.FINISHED, Decision.PROCEED);
        var outcomes = List.of(InquiryOutcome.passed(InquiryType.SHAHKAR), InquiryOutcome.skipped(InquiryType.SEPAD));
        when(inquiries.get(CUSTOMER, REQUEST, done.id())).thenReturn(new InquiryRunView(done, outcomes));

        mvc.perform(get("/api/customer/requests/" + REQUEST + "/inquiries/" + done.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.decision").value("PROCEED"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.inquiries[0].type").value("SHAHKAR"))
                .andExpect(jsonPath("$.inquiries[0].result").value("PASSED"))
                .andExpect(jsonPath("$.inquiries[1].result").value("SKIPPED"));
    }

    @Test
    void aRejectionCarriesTheSrsTextOfTheNegativeInquiry() throws Exception {
        var done = run(RunStatus.FINISHED, Decision.REJECT);
        var outcomes = List.of(InquiryOutcome.passed(InquiryType.SABT_AHVAL),
                InquiryOutcome.negative(InquiryType.BLACKLIST, "inquiry.central-bank-restriction"));
        when(inquiries.get(CUSTOMER, REQUEST, done.id())).thenReturn(new InquiryRunView(done, outcomes));

        mvc.perform(get("/api/customer/requests/" + REQUEST + "/inquiries/" + done.id()))
                .andExpect(jsonPath("$.decision").value("REJECT"))
                .andExpect(jsonPath("$.message").value("مشتری گرامی! به دلیل محدودیت‌های بانک مرکزی، امکان ثبت "
                        + "درخواست صدور ضمانت‌نامه برای شما میسر نیست. لطفاً به آژانس هواپیمایی خود مراجعه کنید."));
    }

    @Test
    void aRejectionWithoutANegativeOutcomeHasNoMessage() throws Exception {
        var done = run(RunStatus.FINISHED, Decision.REJECT);
        when(inquiries.get(CUSTOMER, REQUEST, done.id())).thenReturn(new InquiryRunView(done, List.of()));

        mvc.perform(get("/api/customer/requests/" + REQUEST + "/inquiries/" + done.id()))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void aBlockedRunSaysWhichInquiryFailedAndWhyAndAsksToTryAgain() throws Exception {
        var done = run(RunStatus.FINISHED, Decision.BLOCKED);
        var outcomes = List.of(InquiryOutcome.passed(InquiryType.SHAHKAR),
                InquiryOutcome.failed(InquiryType.SAMAT, FailureKind.TIMEOUT));
        when(inquiries.get(CUSTOMER, REQUEST, done.id())).thenReturn(new InquiryRunView(done, outcomes));

        mvc.perform(get("/api/customer/requests/" + REQUEST + "/inquiries/" + done.id()))
                .andExpect(jsonPath("$.decision").value("BLOCKED"))
                .andExpect(jsonPath("$.message").value("مشتری گرامی! در حال حاضر امکان انجام برخی استعلام‌ها وجود "
                        + "ندارد. لطفاً دقایقی دیگر مجدداً تلاش کنید."))
                .andExpect(jsonPath("$.inquiries[1].failure").value("TIMEOUT"));
    }

    @Test
    void theCooldownIs409WithTheSecondsLeft() throws Exception {
        when(inquiries.start(CUSTOMER, REQUEST)).thenThrow(new InquiryCooldownException(42));

        mvc.perform(post("/api/customer/requests/" + REQUEST + "/inquiries"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("inquiry.cooldown"))
                .andExpect(jsonPath("$.detail").value("لطفاً ۴۲ ثانیه دیگر مجدداً تلاش کنید."));
    }

    @Test
    void aRequestThatIsNotWaitingForInquiriesIs409() throws Exception {
        when(inquiries.start(CUSTOMER, REQUEST)).thenThrow(new RequestNotInquirableException());

        mvc.perform(post("/api/customer/requests/" + REQUEST + "/inquiries")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("request.not-inquirable"));
    }

    @Test
    void anUnknownRunIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(inquiries.get(CUSTOMER, REQUEST, id)).thenThrow(new InquiryRunNotFoundException());

        mvc.perform(get("/api/customer/requests/" + REQUEST + "/inquiries/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void onlyACustomerSessionMayCallIt() {
        assertThat(CustomerInquiryController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('CUSTOMER')");
    }
}
