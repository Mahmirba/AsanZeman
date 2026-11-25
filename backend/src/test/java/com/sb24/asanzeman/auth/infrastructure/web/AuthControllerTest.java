package com.sb24.asanzeman.auth.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.InvalidBankCredentialsException;
import com.sb24.asanzeman.auth.domain.model.AgencyNotRegisteredException;
import com.sb24.asanzeman.auth.domain.model.InvalidSessionException;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;
import com.sb24.asanzeman.auth.domain.port.in.AgencyLoginUseCase;
import com.sb24.asanzeman.auth.domain.model.CustomerChallenge;
import com.sb24.asanzeman.auth.domain.model.OtpWrongException;
import com.sb24.asanzeman.auth.domain.port.in.BankLoginUseCase;
import com.sb24.asanzeman.auth.domain.port.in.CustomerLoginUseCase;
import com.sb24.asanzeman.auth.domain.port.in.LogoutUseCase;
import com.sb24.asanzeman.auth.domain.port.in.RefreshSessionUseCase;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import com.sb24.asanzeman.shared.web.ProblemDetailExceptionHandler;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final IssuedTokens TOKENS = new IssuedTokens("jwt", NOW.plusSeconds(900), "refresh-1",
            NOW.plusSeconds(28800));

    private BankLoginUseCase bankLogin;
    private AgencyLoginUseCase agencyLogin;
    private CustomerLoginUseCase customerLogin;
    private RefreshSessionUseCase refresh;
    private LogoutUseCase logout;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        bankLogin = mock(BankLoginUseCase.class);
        agencyLogin = mock(AgencyLoginUseCase.class);
        customerLogin = mock(CustomerLoginUseCase.class);
        refresh = mock(RefreshSessionUseCase.class);
        logout = mock(LogoutUseCase.class);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders
                .standaloneSetup(new AuthController(bankLogin, agencyLogin, customerLogin, refresh, logout,
                        new MutableClock(NOW)))
                .setControllerAdvice(new ProblemDetailExceptionHandler(messages))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static MockHttpServletRequest postFrom() {
        return new MockHttpServletRequest();
    }

    @Test
    void bankLoginReturnsTheTokensAndPassesTheClientForTheAuditLog() throws Exception {
        when(bankLogin.login(eq("ali@sb24.ir"), eq("pw"), eq("captcha"), any())).thenReturn(TOKENS);

        mvc.perform(post("/api/auth/bank/login").contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Firefox").with(request -> {
                            request.setRemoteAddr("10.0.0.5");
                            return request;
                        })
                        .content("{\"username\":\"ali@sb24.ir\",\"password\":\"pw\",\"arcaptchaToken\":\"captcha\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.refreshToken").value("refresh-1"))
                .andExpect(jsonPath("$.refreshExpiresIn").value(28800));
        verify(bankLogin).login("ali@sb24.ir", "pw", "captcha", new ClientInfo("10.0.0.5", "Firefox"));
    }

    @Test
    void aBankLoginWithMissingFieldsIs400WithTheViolations() throws Exception {
        mvc.perform(post("/api/auth/bank/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ali\",\"password\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.violations.password").exists())
                .andExpect(jsonPath("$.violations.arcaptchaToken").exists());
    }

    @Test
    void wrongCredentialsAre401WithAPersianProblem() throws Exception {
        when(bankLogin.login(any(), any(), any(), any())).thenThrow(new InvalidBankCredentialsException());

        mvc.perform(post("/api/auth/bank/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ali\",\"password\":\"bad\",\"arcaptchaToken\":\"c\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.bank.invalid-credentials"))
                .andExpect(jsonPath("$.detail").value("نام کاربری یا رمز عبور اشتباه است."));
    }

    @Test
    void aBankServiceOutageIs503() throws Exception {
        when(bankLogin.login(any(), any(), any(), any()))
                .thenThrow(new TemporarilyUnavailableException("AAA", new RuntimeException("timeout")));

        mvc.perform(post("/api/auth/bank/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ali\",\"password\":\"pw\",\"arcaptchaToken\":\"c\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void refreshReturnsANewPair() throws Exception {
        when(refresh.refresh(eq("old"), any())).thenReturn(TOKENS);

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"old\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").value("refresh-1"));
    }

    @Test
    void aBadRefreshTokenIs401() throws Exception {
        when(refresh.refresh(eq("used"), any())).thenThrow(new InvalidSessionException());

        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"used\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("auth.session-invalid"));
    }

    @Test
    void aRefreshWithoutATokenIs400() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logoutRevokesTheAccessTokenAndTheSessionItBelongsTo() throws Exception {
        UUID session = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("ali").jti("jti-9").claim("sid", session.toString())
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(900)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());

        verify(logout).logout(eq("jti-9"), eq(NOW.plusSeconds(900)), eq("ali"), eq(session), any());
        assertThat(postFrom().getRemoteAddr()).isNotNull();
    }

    @Test
    void aBodyOnLogoutIsIgnoredAndATokenWithoutASessionStillLogsOut() throws Exception {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("ali").jti("jti-9").issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(900)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"r1\"}")).andExpect(status().isNoContent());

        verify(logout).logout(eq("jti-9"), eq(NOW.plusSeconds(900)), eq("ali"), eq(null), any());
    }

    @Test
    void agencyLoginReturnsTheTokens() throws Exception {
        when(agencyLogin.login(eq("agency"), eq("pw"), eq("captcha"), any())).thenReturn(TOKENS);

        mvc.perform(post("/api/auth/agency/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agency\",\"password\":\"pw\",\"arcaptchaToken\":\"captcha\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"));
    }

    @Test
    void anUnregisteredAgencyIs403WithAPersianProblem() throws Exception {
        when(agencyLogin.login(any(), any(), any(), any())).thenThrow(new AgencyNotRegisteredException());

        mvc.perform(post("/api/auth/agency/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agency\",\"password\":\"pw\",\"arcaptchaToken\":\"c\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("auth.agency.not-registered"));
    }

    @Test
    void customerLoginSendsTheCodeAndReturnsTheChallenge() throws Exception {
        UUID id = UUID.randomUUID();
        when(customerLogin.login("1000000011", "pw", "captcha")).thenReturn(new CustomerChallenge(id, 120));

        mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"1000000011\",\"password\":\"pw\",\"arcaptchaToken\":\"captcha\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value(id.toString()))
                .andExpect(jsonPath("$.expiresInSeconds").value(120));
    }

    @Test
    void aCustomerLoginWithMissingFieldsIs400() throws Exception {
        mvc.perform(post("/api/auth/customer/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"1000000011\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.password").exists());
    }

    @Test
    void verifyingTheCodeReturnsASessionWithoutARefreshToken() throws Exception {
        UUID id = UUID.randomUUID();
        when(customerLogin.verifyOtp(eq(id), eq("1234"), eq(new ClientInfo("10.0.0.5", "Firefox"))))
                .thenReturn(new IssuedTokens("jwt", NOW.plusSeconds(1800), null, NOW.plusSeconds(1800)));

        mvc.perform(post("/api/auth/customer/otp/verify").contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Firefox").with(request -> {
                            request.setRemoteAddr("10.0.0.5");
                            return request;
                        })
                        .content("{\"challengeId\":\"" + id + "\",\"code\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.refreshExpiresIn").value(0));
    }

    @Test
    void aWrongCodeIs400WithAPersianProblem() throws Exception {
        when(customerLogin.verifyOtp(any(), any(), any())).thenThrow(new OtpWrongException());

        mvc.perform(post("/api/auth/customer/otp/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + UUID.randomUUID() + "\",\"code\":\"0000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.otp.wrong"))
                .andExpect(jsonPath("$.detail").value("کد تأیید وارد شده صحیح نیست."));
    }

    @Test
    void aVerifyWithoutACodeIs400() throws Exception {
        mvc.perform(post("/api/auth/customer/otp/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.code").exists());
    }

    @Test
    void anAgencyLoginWithMissingFieldsIs400() throws Exception {
        mvc.perform(post("/api/auth/agency/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agency\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations.password").exists());
    }
}
