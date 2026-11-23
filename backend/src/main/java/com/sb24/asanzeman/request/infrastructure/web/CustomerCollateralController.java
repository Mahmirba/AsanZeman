package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.port.in.ChooseCollateralCommand;
import com.sb24.asanzeman.request.domain.port.in.CustomerCollateralUseCase;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer's collateral steps (4.3): the deposits they can offer, the choice, and the submit. Only a CUSTOMER
 * session may call it, for the customer's own request; the customer is the token's {@code sub} and their Yaghut session
 * is found through the token's {@code sid}.
 */
@RestController
@RequestMapping("/api/customer/requests/{requestId}")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerCollateralController {

    private final CustomerCollateralUseCase collateral;
    private final MessageSource messages;

    public CustomerCollateralController(CustomerCollateralUseCase collateral, MessageSource messages) {
        this.collateral = collateral;
        this.messages = messages;
    }

    @GetMapping("/deposits")
    public List<CollateralResponses.Deposit> deposits(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        return collateral.deposits(jwt.getSubject(), session(jwt), requestId).stream()
                .map(CollateralResponses.Deposit::from).toList();
    }

    @PostMapping("/collateral")
    public CollateralResponses.Plan choose(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId,
            @Valid @RequestBody ChooseCollateralBody body) {
        return CollateralResponses.Plan.from(collateral.choose(jwt.getSubject(), session(jwt), requestId,
                new ChooseCollateralCommand(body.cashPercent(), body.cashDeposits(), body.pledgeDeposits())));
    }

    @PostMapping("/submit")
    public CollateralResponses.Submitted submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID requestId) {
        RequestStatus status = collateral.submit(jwt.getSubject(), requestId);
        return new CollateralResponses.Submitted(status.name(),
                RequestSummaryResponse.label(messages, "status", status));
    }

    private static UUID session(Jwt jwt) {
        return UUID.fromString(jwt.getClaimAsString("sid"));
    }
}
