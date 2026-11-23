package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.port.in.CustomerRequestsUseCase;
import java.util.List;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer panel's requests. Only a CUSTOMER session may call it. The customer's national code is the token's
 * {@code sub} (set at login, 6.3), never a request value, so a customer can only ever read their own requests: another
 * customer's request is a plain 404.
 */
@RestController
@RequestMapping("/api/customer/requests")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerRequestController {

    private final CustomerRequestsUseCase requests;
    private final MessageSource messages;

    public CustomerRequestController(CustomerRequestsUseCase requests, MessageSource messages) {
        this.requests = requests;
        this.messages = messages;
    }

    @GetMapping
    public List<RequestSummaryResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return requests.list(jwt.getSubject()).stream().map(s -> RequestSummaryResponse.from(s, messages)).toList();
    }

    @GetMapping("/{id}")
    public RequestDetailsResponse details(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return RequestDetailsResponse.from(requests.get(jwt.getSubject(), id), messages);
    }
}
