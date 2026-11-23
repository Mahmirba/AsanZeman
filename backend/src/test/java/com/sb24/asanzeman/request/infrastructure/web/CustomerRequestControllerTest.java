package com.sb24.asanzeman.request.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.RequestDetails;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.port.in.CustomerRequestsUseCase;
import com.sb24.asanzeman.shared.web.ProblemDetailExceptionHandler;
import java.time.Instant;
import java.time.LocalDate;
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

class CustomerRequestControllerTest {

    private static final String CUSTOMER = "1000000011";
    private static final RequestSummary SUMMARY = new RequestSummary(UUID.randomUUID(), "012300420509020001",
            RequestType.ISSUANCE, RequestStatus.REGISTERED_AT_AGENCY, CUSTOMER, "555", "Ali Rezaei", 250_000_000L,
            "0123", "Valiasr", LocalDate.of(2027, 3, 11), Instant.parse("2026-11-23T08:00:00Z"), "Sky Travel",
            "09121234567");

    private CustomerRequestsUseCase requests;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        requests = mock(CustomerRequestsUseCase.class);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new CustomerRequestController(requests, messages))
                .setControllerAdvice(new ProblemDetailExceptionHandler(messages))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject(CUSTOMER).claim("role", "CUSTOMER")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(1800)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsTheRequestsOfTheCustomerInTheToken() throws Exception {
        when(requests.list(CUSTOMER)).thenReturn(List.of(SUMMARY));

        mvc.perform(get("/api/customer/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(SUMMARY.id().toString()))
                .andExpect(jsonPath("$[0].trackingCode").value("012300420509020001"))
                .andExpect(jsonPath("$[0].status").value("REGISTERED_AT_AGENCY"))
                .andExpect(jsonPath("$[0].statusLabel").value("ثبت درخواست در پنل آژانس"))
                .andExpect(jsonPath("$[0].agencyName").value("Sky Travel"));
    }

    @Test
    void anEmptyListIsAnEmptyArray() throws Exception {
        when(requests.list(CUSTOMER)).thenReturn(List.of());

        mvc.perform(get("/api/customer/requests")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void detailsCarryTheCompanions() throws Exception {
        var companion = new Companion("Sara Rezaei", "0012345679", LocalDate.of(2016, 4, 1), "P1234567");
        when(requests.get(CUSTOMER, SUMMARY.id())).thenReturn(new RequestDetails(SUMMARY, List.of(companion)));

        mvc.perform(get("/api/customer/requests/" + SUMMARY.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.trackingCode").value("012300420509020001"))
                .andExpect(jsonPath("$.companions[0].fullName").value("Sara Rezaei"));
    }

    @Test
    void anotherCustomersRequestIsA404() throws Exception {
        UUID id = UUID.randomUUID();
        when(requests.get(CUSTOMER, id)).thenThrow(new RequestNotFoundException());

        mvc.perform(get("/api/customer/requests/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void onlyACustomerSessionMayCallIt() {
        assertThat(CustomerRequestController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('CUSTOMER')");
    }
}
