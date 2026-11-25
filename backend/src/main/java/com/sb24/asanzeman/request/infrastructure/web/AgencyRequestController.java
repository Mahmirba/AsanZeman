package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestCommand;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestResult;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestUseCase;
import com.sb24.asanzeman.request.domain.port.in.CompanionInput;
import com.sb24.asanzeman.request.domain.port.in.GetRequestUseCase;
import com.sb24.asanzeman.request.domain.port.in.RegisterRequestCommand;
import com.sb24.asanzeman.request.domain.port.in.RegisterRequestUseCase;
import com.sb24.asanzeman.request.domain.port.in.RegisteredRequest;
import com.sb24.asanzeman.request.domain.port.in.SearchRequestsUseCase;
import com.sb24.asanzeman.shared.model.JalaliDate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * The agency panel's requests (SRS UC-AZS-8 and UC-AZS-13). Only an AGENCY session may call it; the agency, its name
 * and its session come from the access token ({@code uid}, {@code sub}, {@code sid}), never from the request. An
 * agency can only ever read or change its own requests: another agency's request is a plain 404.
 */
@RestController
@RequestMapping("/api/agency/requests")
@PreAuthorize("hasRole('AGENCY')")
public class AgencyRequestController {

    private final RegisterRequestUseCase register;
    private final SearchRequestsUseCase search;
    private final GetRequestUseCase get;
    private final CancelRequestUseCase cancel;
    private final MessageSource messages;

    public AgencyRequestController(RegisterRequestUseCase register, SearchRequestsUseCase search,
            GetRequestUseCase get, CancelRequestUseCase cancel, MessageSource messages) {
        this.register = register;
        this.search = search;
        this.get = get;
        this.cancel = cancel;
        this.messages = messages;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredRequest register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterRequestBody body,
            HttpServletRequest http) {
        List<CompanionInput> companions = body.companions() == null ? List.of()
                : body.companions().stream().map(c -> new CompanionInput(c.fullName(), c.nationalCode(),
                        date(c.birthDate()), c.passportNumber())).toList();
        return register.register(new RegisterRequestCommand(agency(jwt), session(jwt), actor(jwt),
                body.nationalCode(), body.branchCode(), body.amount(), date(body.dueDate()), companions,
                http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT)));
    }

    /** The tracking list of SRS UC-AZS-13, newest first. Every filter is optional. */
    @GetMapping
    public RequestPageResponse list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String nationalCode, @RequestParam(required = false) String customerNumber,
            @RequestParam(required = false) List<String> type, @RequestParam(required = false) String trackingCode,
            @RequestParam(required = false) String name, @RequestParam(required = false) String registeredFrom,
            @RequestParam(required = false) String registeredTo, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var criteria = RequestFilters.of(agency(jwt), nationalCode, customerNumber, type, trackingCode, name,
                registeredFrom, registeredTo);
        return RequestPageResponse.from(search.search(criteria, page, size), messages);
    }

    /** The same search as a CSV file (D42): every matching row, streamed. */
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String nationalCode, @RequestParam(required = false) String customerNumber,
            @RequestParam(required = false) List<String> type, @RequestParam(required = false) String trackingCode,
            @RequestParam(required = false) String name, @RequestParam(required = false) String registeredFrom,
            @RequestParam(required = false) String registeredTo) {
        // the filters are checked here, before the first byte is written, so a bad filter is a normal 400
        var criteria = RequestFilters.of(agency(jwt), nationalCode, customerNumber, type, trackingCode, name,
                registeredFrom, registeredTo);
        StreamingResponseBody body = out -> {
            try (RequestCsvWriter csv = new RequestCsvWriter(out, messages)) {
                search.export(criteria, page -> {
                    try {
                        csv.write(page);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            }
        };
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("requests.csv").build().toString())
                .body(body);
    }

    @GetMapping("/{id}")
    public RequestDetailsResponse details(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return RequestDetailsResponse.from(get.get(agency(jwt), id), messages);
    }

    /** The agency's one «ثبت درخواست ابطال» action (4.2): cancel before «اقدام», revocation request after it. */
    @PostMapping("/{id}/cancel")
    public CancelRequestResult cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            HttpServletRequest http) {
        return cancel.cancel(new CancelRequestCommand(agency(jwt), jwt.getSubject(), id, http.getRemoteAddr(),
                http.getHeader(HttpHeaders.USER_AGENT), actor(jwt)));
    }

    static UUID agency(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("uid"));
    }

    /** The person who logged in for the agency, from the token; the agency name for a token that has none. */
    static String actor(Jwt jwt) {
        String actor = jwt.getClaimAsString("usr");
        return actor == null ? jwt.getSubject() : actor;
    }

    static UUID session(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("sid"));
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
