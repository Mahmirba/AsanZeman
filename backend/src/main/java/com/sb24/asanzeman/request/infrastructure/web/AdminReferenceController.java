package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.AdminAgency;
import com.sb24.asanzeman.request.domain.model.AdminReason;
import com.sb24.asanzeman.request.domain.model.AgencyData;
import com.sb24.asanzeman.request.domain.model.ReasonData;
import com.sb24.asanzeman.request.domain.port.in.AdminActor;
import com.sb24.asanzeman.request.domain.port.in.AdminReferenceUseCase;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The admin's agencies and rejection reasons. The admin is taken from the access token. The values are checked when the
 * body is turned into {@link AgencyData} or {@link ReasonData}, so a bad value is a 400 before anything is saved.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminReferenceController {

    private final AdminReferenceUseCase admin;

    public AdminReferenceController(AdminReferenceUseCase admin) {
        this.admin = admin;
    }

    /** An agency as the admin sends it; {@code active} is on unless it says otherwise. */
    public record AgencyBody(String cif, String code, String name, Boolean active, String mobile) {

        AgencyData toData() {
            return new AgencyData(cif, code, name, active == null || active, mobile);
        }
    }

    /** A rejection reason as the admin sends it; {@code active} is on unless it says otherwise. */
    public record ReasonBody(String kind, String text, Boolean active, Integer sortOrder) {

        ReasonData toData() {
            return new ReasonData(kind, text, active == null || active, sortOrder == null ? 0 : sortOrder);
        }
    }

    public record AgencyResponse(UUID id, String cif, String code, String name, boolean active, String mobile) {

        static AgencyResponse from(AdminAgency a) {
            var d = a.data();
            return new AgencyResponse(a.id(), d.cif(), d.code(), d.name(), d.active(), d.mobile());
        }
    }

    public record ReasonResponse(UUID id, String kind, String text, boolean active, int sortOrder) {

        static ReasonResponse from(AdminReason r) {
            var d = r.data();
            return new ReasonResponse(r.id(), d.kind(), d.text(), d.active(), d.sortOrder());
        }
    }

    @GetMapping("/agencies")
    public List<AgencyResponse> agencies() {
        return admin.agencies().stream().map(AgencyResponse::from).toList();
    }

    @PostMapping("/agencies")
    @ResponseStatus(HttpStatus.CREATED)
    public AgencyResponse createAgency(@AuthenticationPrincipal Jwt jwt, @RequestBody AgencyBody body,
            HttpServletRequest http) {
        return AgencyResponse.from(admin.createAgency(actor(jwt, http), body.toData()));
    }

    @PutMapping("/agencies/{id}")
    public AgencyResponse updateAgency(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestBody AgencyBody body, HttpServletRequest http) {
        return AgencyResponse.from(admin.updateAgency(actor(jwt, http), id, body.toData()));
    }

    @GetMapping("/rejection-reasons")
    public List<ReasonResponse> reasons() {
        return admin.reasons().stream().map(ReasonResponse::from).toList();
    }

    @PostMapping("/rejection-reasons")
    @ResponseStatus(HttpStatus.CREATED)
    public ReasonResponse createReason(@AuthenticationPrincipal Jwt jwt, @RequestBody ReasonBody body,
            HttpServletRequest http) {
        return ReasonResponse.from(admin.createReason(actor(jwt, http), body.toData()));
    }

    @PutMapping("/rejection-reasons/{id}")
    public ReasonResponse updateReason(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @RequestBody ReasonBody body, HttpServletRequest http) {
        return ReasonResponse.from(admin.updateReason(actor(jwt, http), id, body.toData()));
    }

    private static AdminActor actor(Jwt jwt, HttpServletRequest http) {
        return new AdminActor(UUID.fromString(jwt.getClaimAsString("uid")), jwt.getSubject(), http.getRemoteAddr(),
                http.getHeader(HttpHeaders.USER_AGENT));
    }
}
