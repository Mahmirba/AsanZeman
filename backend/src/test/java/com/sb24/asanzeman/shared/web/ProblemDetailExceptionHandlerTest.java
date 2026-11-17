package com.sb24.asanzeman.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sb24.asanzeman.shared.model.DomainException;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import com.sb24.asanzeman.shared.validation.IranianNationalCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** D48: HTTP-level contract of the error handling, including the security filter chain. */
@WebMvcTest(controllers = ProblemDetailExceptionHandlerTest.ProbeController.class)
@Import({ProblemDetailExceptionHandlerTest.ProbeController.class, ProblemDetailExceptionHandler.class,
        ProblemDetailSecurityHandlers.class, com.sb24.asanzeman.shared.config.SecurityConfiguration.class})
@TestPropertySource(locations = "classpath:application-test.properties")
class ProblemDetailExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    static final class LockedException extends DomainException {
        LockedException(String holder) {
            super("request.locked-by-other", Kind.CONFLICT, holder);
        }
    }

    static final class MissingException extends DomainException {
        MissingException() {
            super("request.not-found", Kind.NOT_FOUND);
        }
    }

    static final class RuleException extends DomainException {
        RuleException() {
            super("collateral.cash-below-minimum", Kind.BAD_REQUEST);
        }
    }

    static final class OwnershipException extends DomainException {
        OwnershipException() {
            super("request.not-yours", Kind.FORBIDDEN);
        }
    }

    record Form(@NotBlank String name, @IranianNationalCode String nationalCode) {
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/auth/x/login")
        String locked() { throw new LockedException("rezaei"); }

        @GetMapping("/api/auth/missing/login")
        String missing() { throw new MissingException(); }

        @GetMapping("/api/auth/rule/login")
        String rule() { throw new RuleException(); }

        @GetMapping("/api/auth/own/login")
        String own() { throw new OwnershipException(); }

        @GetMapping("/api/auth/down/login")
        String down() { throw new TemporarilyUnavailableException("Yaghut", new RuntimeException("boom")); }

        @GetMapping("/api/auth/denied/login")
        String denied() { throw new AccessDeniedException("no"); }

        @GetMapping("/api/auth/npe/login")
        String npe() { throw new NullPointerException("secret internals"); }

        @PostMapping("/api/auth/form/login")
        String form(@Valid @RequestBody Form form) { return "ok"; }

        @GetMapping("/api/protected")
        String protectedPath() { return "ok"; }
    }

    @Test
    void domainConflictBecomesProblem409WithPersianDetailAndCode() throws Exception {
        mvc.perform(get("/api/auth/x/login"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.code").value("request.locked-by-other"))
                .andExpect(jsonPath("$.type").value("urn:asanzeman:error:request.locked-by-other"))
                .andExpect(jsonPath("$.instance").value("/api/auth/x/login"))
                // no Persian text for this test-only code yet: the code itself is the fallback
                .andExpect(jsonPath("$.detail").value("request.locked-by-other"));
    }

    @Test
    void everyKindMapsToItsStatus() throws Exception {
        mvc.perform(get("/api/auth/missing/login")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("request.not-found"));
        mvc.perform(get("/api/auth/rule/login")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("collateral.cash-below-minimum"));
        mvc.perform(get("/api/auth/own/login")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("request.not-yours"));
    }

    @Test
    @DisplayName("technical failure of a bank service is 503 with the generic Persian text, never 400")
    void unavailableIs503() throws Exception {
        mvc.perform(get("/api/auth/down/login"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("service.temporarily-unavailable"))
                .andExpect(jsonPath("$.detail").value("در حال حاضر امکان ارتباط با سرویس Yaghut وجود ندارد. لطفاً بعداً تلاش کنید."));
    }

    @Test
    void methodSecurityDenialIs403() throws Exception {
        mvc.perform(get("/api/auth/denied/login"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("access.denied"))
                .andExpect(jsonPath("$.detail").value("شما مجوز انجام این عملیات را ندارید."));
    }

    @Test
    @DisplayName("unexpected exceptions are 500 without leaking the message")
    void unexpectedIs500Generic() throws Exception {
        mvc.perform(get("/api/auth/npe/login"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("internal.error"))
                .andExpect(jsonPath("$.detail").value("خطای داخلی رخ داده است. لطفاً بعداً تلاش کنید."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret internals"))));
    }

    @Test
    void validationErrorsListViolationsWithResolvedPersianMessages() throws Exception {
        mvc.perform(post("/api/auth/form/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"nationalCode\":\"1000000012\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.detail").value("اطلاعات ورودی معتبر نیست."))
                .andExpect(jsonPath("$.violations.nationalCode").value("کد ملی وارد شده معتبر نیست."))
                .andExpect(jsonPath("$.violations.name").isNotEmpty());
    }

    @Test
    void malformedJsonAndWrongMethodUseTheSameShape() throws Exception {
        mvc.perform(post("/api/auth/form/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("http.400"))
                .andExpect(jsonPath("$.detail").value("درخواست نامعتبر است."));
        mvc.perform(post("/api/auth/x/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("http.405"));
    }

    @Test
    @DisplayName("the security chain answers 401 as Problem Details and never sets a cookie (D7)")
    void unauthenticatedIsProblem401() throws Exception {
        mvc.perform(get("/api/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("auth.unauthenticated"))
                .andExpect(jsonPath("$.detail").value("برای دسترسی به این بخش باید وارد سامانه شوید."))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}
