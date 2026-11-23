package com.sb24.asanzeman.request.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sb24.asanzeman.request.domain.model.AdminAgency;
import com.sb24.asanzeman.request.domain.model.AdminItemNotFoundException;
import com.sb24.asanzeman.request.domain.model.AdminReason;
import com.sb24.asanzeman.request.domain.model.AgencyData;
import com.sb24.asanzeman.request.domain.model.AgencyDuplicateException;
import com.sb24.asanzeman.request.domain.model.ReasonData;
import com.sb24.asanzeman.request.domain.port.in.AdminActor;
import com.sb24.asanzeman.request.domain.port.in.AdminReferenceUseCase;
import com.sb24.asanzeman.shared.web.ProblemDetailExceptionHandler;
import java.time.Instant;
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

class AdminReferenceControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    private AdminReferenceUseCase admin;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        admin = mock(AdminReferenceUseCase.class);
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new AdminReferenceController(admin))
                .setControllerAdvice(new ProblemDetailExceptionHandler(messages))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("root").claim("uid", ADMIN.toString())
                .claim("role", "ADMIN").issuedAt(NOW).expiresAt(NOW.plusSeconds(900)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static final String AGENCY_JSON = """
            {"cif":"9001","code":"0042","name":"Sky Travel","mobile":"09121234567"}""";
    private static final AgencyData AGENCY = new AgencyData("9001", "0042", "Sky Travel", true, "09121234567");

    @Test
    void listsTheAgenciesAndTheReasons() throws Exception {
        when(admin.agencies()).thenReturn(List.of(new AdminAgency(ID, AGENCY)));
        when(admin.reasons()).thenReturn(List.of(new AdminReason(ID, new ReasonData("REVOCATION", "Still in use",
                false, 2))));

        mvc.perform(get("/api/admin/agencies")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("0042")).andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].mobile").value("09121234567"));
        mvc.perform(get("/api/admin/rejection-reasons")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("REVOCATION")).andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].sortOrder").value(2));
    }

    @Test
    void createsAnAgencyActiveByDefaultAsTheAdminFromTheToken() throws Exception {
        when(admin.createAgency(any(), eq(AGENCY))).thenReturn(new AdminAgency(ID, AGENCY));

        mvc.perform(post("/api/admin/agencies").contentType(MediaType.APPLICATION_JSON).content(AGENCY_JSON)
                .header("User-Agent", "Firefox").with(r -> {
                    r.setRemoteAddr("10.0.0.9");
                    return r;
                })).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(ID.toString()));

        var actor = ArgumentCaptor.forClass(AdminActor.class);
        verify(admin).createAgency(actor.capture(), eq(AGENCY));
        assertThat(actor.getValue()).isEqualTo(new AdminActor(ADMIN, "root", "10.0.0.9", "Firefox"));
    }

    @Test
    void updatesAnAgencyAndCanSwitchItOff() throws Exception {
        var off = new AgencyData("9001", "0042", "Sky Travel", false, null);
        when(admin.updateAgency(any(), eq(ID), eq(off))).thenReturn(new AdminAgency(ID, off));

        mvc.perform(put("/api/admin/agencies/" + ID).contentType(MediaType.APPLICATION_JSON)
                .content("{\"cif\":\"9001\",\"code\":\"0042\",\"name\":\"Sky Travel\",\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void createsAndUpdatesAReasonWithTheOrderDefaultingToZero() throws Exception {
        var data = new ReasonData("ISSUANCE", "Incomplete", true, 0);
        when(admin.createReason(any(), eq(data))).thenReturn(new AdminReason(ID, data));
        when(admin.updateReason(any(), eq(ID), eq(data))).thenReturn(new AdminReason(ID, data));
        String body = "{\"kind\":\"ISSUANCE\",\"text\":\"Incomplete\"}";

        mvc.perform(post("/api/admin/rejection-reasons").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.text").value("Incomplete"));
        mvc.perform(put("/api/admin/rejection-reasons/" + ID).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sortOrder").value(0));
    }

    @Test
    void aBadValueIsA400AndNothingReachesTheUseCase() throws Exception {
        mvc.perform(post("/api/admin/agencies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"cif\":\"9001\",\"code\":\"42\",\"name\":\"Sky\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/rejection-reasons").contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"OTHER\",\"text\":\"t\"}")).andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(admin);
    }

    @Test
    void aTakenAgencyIsA409AndAnUnknownOneA404() throws Exception {
        when(admin.createAgency(any(), any())).thenThrow(new AgencyDuplicateException());
        when(admin.updateAgency(any(), eq(ID), any())).thenThrow(new AdminItemNotFoundException());

        mvc.perform(post("/api/admin/agencies").contentType(MediaType.APPLICATION_JSON).content(AGENCY_JSON))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/admin/agencies/" + ID).contentType(MediaType.APPLICATION_JSON).content(AGENCY_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAnAdminMayUseIt() {
        assertThat(AdminReferenceController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasRole('ADMIN')");
    }
}
