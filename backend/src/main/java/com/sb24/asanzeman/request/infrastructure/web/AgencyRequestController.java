package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.port.in.CompanionInput;
import com.sb24.asanzeman.request.domain.port.in.RegisterRequestCommand;
import com.sb24.asanzeman.request.domain.port.in.RegisterRequestUseCase;
import com.sb24.asanzeman.request.domain.port.in.RegisteredRequest;
import com.sb24.asanzeman.shared.model.JalaliDate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The agency panel's requests (SRS UC-AZS-8). Only an AGENCY session may call it; the agency, its name and its
 * session come from the access token ({@code uid}, {@code sub}, {@code sid}), never from the body.
 */
@RestController
@RequestMapping("/api/agency/requests")
@PreAuthorize("hasRole('AGENCY')")
public class AgencyRequestController {

    private final RegisterRequestUseCase register;

    public AgencyRequestController(RegisterRequestUseCase register) {
        this.register = register;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredRequest register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterRequestBody body,
            HttpServletRequest http) {
        List<CompanionInput> companions = body.companions() == null ? List.of()
                : body.companions().stream().map(c -> new CompanionInput(c.fullName(), c.nationalCode(),
                        date(c.birthDate()), c.passportNumber())).toList();
        return register.register(new RegisterRequestCommand(UUID.fromString(jwt.getClaimAsString("uid")),
                UUID.fromString(jwt.getClaimAsString("sid")), jwt.getSubject(), body.nationalCode(),
                body.branchCode(), body.amount(), date(body.dueDate()), companions, http.getRemoteAddr(),
                http.getHeader(HttpHeaders.USER_AGENT)));
    }

    /** A Jalali {@code yyyy/MM/dd} date as a Gregorian one; anything else is a 400. */
    private static LocalDate date(String jalali) {
        try {
            return JalaliDate.parse(jalali).toGregorian();
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestDataException("a date must be a real Jalali date, yyyy/MM/dd");
        }
    }
}
