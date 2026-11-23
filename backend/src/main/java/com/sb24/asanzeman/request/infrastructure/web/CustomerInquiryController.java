package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.port.in.CustomerInquiryUseCase;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer's inquiries (5.1). The run happens in the background: the start returns 202 at once with the run, and the
 * panel polls it. A slow service cannot push the page past the proxy's HTTP timeout. Only a CUSTOMER session may call
 * it, and only for the customer's own requests (the token's {@code sub} is the national code).
 */
@RestController
@RequestMapping("/api/customer/requests/{requestId}/inquiries")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerInquiryController {

    private final CustomerInquiryUseCase inquiries;
    private final MessageSource messages;

    public CustomerInquiryController(CustomerInquiryUseCase inquiries, MessageSource messages) {
        this.inquiries = inquiries;
        this.messages = messages;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public InquiryRunResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        return InquiryRunResponse.from(inquiries.start(jwt.getSubject(), requestId), messages);
    }

    @GetMapping("/{runId}")
    public InquiryRunResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId,
            @PathVariable UUID runId) {
        return InquiryRunResponse.from(inquiries.get(jwt.getSubject(), requestId, runId), messages);
    }
}
