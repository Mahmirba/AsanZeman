package com.sb24.asanzeman.request.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException;
import com.sb24.asanzeman.request.domain.model.AgencyNotActiveException;
import com.sb24.asanzeman.request.domain.model.BranchSummary;
import com.sb24.asanzeman.request.domain.model.CustomerUnderageException;
import com.sb24.asanzeman.request.domain.model.NotBankCustomerException;
import com.sb24.asanzeman.request.domain.port.in.AgencyProfile;
import com.sb24.asanzeman.request.domain.port.in.AgencyProfileUseCase;
import com.sb24.asanzeman.request.domain.port.in.CustomerLookupResult;
import com.sb24.asanzeman.request.domain.port.in.ListBranchesUseCase;
import com.sb24.asanzeman.request.domain.port.in.LookupCustomerUseCase;
import com.sb24.asanzeman.shared.web.ProblemDetailExceptionHandler;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgencyPanelControllerTest {

    private static final UUID AGENCY = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();
    private static final String CODE = "0012345679";
    private static final String BODY = "{\"nationalCode\":\"" + CODE + "\"}";

    private AgencyProfileUseCase profile;
    private ListBranchesUseCase branches;
    private LookupCustomerUseCase lookup;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        profile = mock(AgencyProfileUseCase.class);
        branches = mock(ListBranchesUseCase.class);
        lookup = mock(LookupCustomerUseCase.class);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new AgencyPanelController(profile, branches, lookup))
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

    @Test
    void theProfileIsTheAgencyNameAndCode() throws Exception {
        when(profile.profile(AGENCY)).thenReturn(new AgencyProfile("Sky Travel", "0042"));

        mvc.perform(get("/api/agency/profile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sky Travel")).andExpect(jsonPath("$.code").value("0042"));
    }

    @Test
    void aDeactivatedAgencyIs403() throws Exception {
        doThrow(new AgencyNotActiveException()).when(profile).profile(any());

        mvc.perform(get("/api/agency/profile")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("request.agency-not-active"));
    }

    @Test
    void theBranchListPassesTheTypedLetters() throws Exception {
        when(branches.listOpen("Val")).thenReturn(List.of(new BranchSummary("0123", "Valiasr", "Tehran")));

        mvc.perform(get("/api/agency/branches").param("q", "Val")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("0123")).andExpect(jsonPath("$[0].name").value("Valiasr"))
                .andExpect(jsonPath("$[0].city").value("Tehran"));
    }

    @Test
    void withoutAQueryTheWholeOpenListIsAsked() throws Exception {
        when(branches.listOpen(null)).thenReturn(List.of());

        mvc.perform(get("/api/agency/branches")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        verify(branches).listOpen(null);
    }

    @Test
    void theLookupShowsTheCustomerWithAJalaliBirthDateUsingTheSessionFromTheToken() throws Exception {
        when(lookup.lookup(SESSION, CODE)).thenReturn(new CustomerLookupResult("555", CODE, "Ali Rezaei",
                LocalDate.of(1990, 5, 17), "09121234567"));

        mvc.perform(post("/api/agency/customers/lookup").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cif").value("555"))
                .andExpect(jsonPath("$.nationalCode").value(CODE))
                .andExpect(jsonPath("$.fullName").value("Ali Rezaei"))
                .andExpect(jsonPath("$.birthDate").value("1369/02/27"))
                .andExpect(jsonPath("$.mobile").value("09121234567"));
    }

    @Test
    void aMissingBirthDateStaysNull() throws Exception {
        when(lookup.lookup(SESSION, CODE)).thenReturn(new CustomerLookupResult(null, CODE, null, null, null));

        mvc.perform(post("/api/agency/customers/lookup").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.birthDate").doesNotExist());
    }

    @Test
    void theLookupRefusalsKeepTheirSrsTexts() throws Exception {
        doThrow(new NotBankCustomerException()).when(lookup).lookup(any(), any());
        mvc.perform(post("/api/agency/customers/lookup").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("اطلاعات وارد شده مربوط به مشتری بانک نیست."));

        doThrow(new CustomerUnderageException()).when(lookup).lookup(any(), any());
        mvc.perform(post("/api/agency/customers/lookup").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("request.customer-underage"));

        doThrow(new YaghutSessionExpiredException()).when(lookup).lookup(any(), any());
        mvc.perform(post("/api/agency/customers/lookup").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aBlankNationalCodeIs400() throws Exception {
        mvc.perform(post("/api/agency/customers/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nationalCode\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.violations.nationalCode").exists());
    }

    @Test
    void onlyAnAgencySessionMayCallIt() {
        assertThat(AgencyPanelController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('AGENCY')");
    }
}
