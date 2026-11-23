package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.BranchUser;
import com.sb24.asanzeman.request.domain.port.in.BranchCommand;
import com.sb24.asanzeman.request.domain.port.in.BranchQueryUseCase;
import com.sb24.asanzeman.request.domain.port.in.BranchReviewUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The branch panel (SRS review use case, 4.1, 4.4). Branch and admin users only. Who the user is, their name and their
 * branch come from the access token ({@code uid}, {@code sub}, {@code branch_code}), never from the request. Every
 * action runs in one short transaction under the request's row lock; the ownership lock between calls is in the data.
 */
@RestController
@RequestMapping("/api/branch")
@PreAuthorize("hasAnyRole('BRANCH','ADMIN')")
public class BranchRequestController {

    private final BranchQueryUseCase queries;
    private final BranchReviewUseCase reviews;
    private final MessageSource messages;

    public BranchRequestController(BranchQueryUseCase queries, BranchReviewUseCase reviews, MessageSource messages) {
        this.queries = queries;
        this.reviews = reviews;
        this.messages = messages;
    }

    /** The inbox when no status is given (waiting, in review, in action), or a search by status and the other filters. */
    @GetMapping("/requests")
    public BranchResponses.Page list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String nationalCode, @RequestParam(required = false) String customerNumber,
            @RequestParam(required = false) String trackingCode, @RequestParam(required = false) String registeredFrom,
            @RequestParam(required = false) String registeredTo, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        BranchUser user = user(jwt);
        var criteria = BranchFilters.of(status, nationalCode, customerNumber, trackingCode, registeredFrom,
                registeredTo);
        return BranchResponses.Page.from(queries.search(user, criteria, page, size), user, messages);
    }

    @GetMapping("/requests/{id}")
    public BranchResponses.Details details(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        BranchUser user = user(jwt);
        return BranchResponses.Details.from(queries.get(user, id), user, messages);
    }

    @GetMapping("/rejection-reasons")
    public List<BranchResponses.Reason> rejectionReasons(@RequestParam(defaultValue = "ISSUANCE") String kind) {
        return queries.rejectionReasons(kind.toUpperCase()).stream().map(BranchResponses.Reason::from).toList();
    }

    /** «اقدام». */
    @PostMapping("/requests/{id}/lock")
    public BranchResponses.Result take(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            HttpServletRequest http) {
        return BranchResponses.Result.from(reviews.take(command(jwt, id, http)), messages);
    }

    /** «تایید درخواست صدور». */
    @PostMapping("/requests/{id}/approve")
    public BranchResponses.Result approve(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            HttpServletRequest http) {
        return BranchResponses.Result.from(reviews.approve(command(jwt, id, http)), messages);
    }

    /** «رد درخواست صدور», with a reason from the admin's list and a comment of at most 200 characters. */
    @PostMapping("/requests/{id}/reject")
    public BranchResponses.Result reject(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody RejectBody body, HttpServletRequest http) {
        return BranchResponses.Result.from(reviews.reject(command(jwt, id, http), body.reasonId(), body.comment()),
                messages);
    }

    /** The guarantee number from Farangin: «صادر شده». */
    @PostMapping("/requests/{id}/issue")
    public BranchResponses.Result issue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody IssueBody body, HttpServletRequest http) {
        return BranchResponses.Result.from(reviews.issue(command(jwt, id, http), body.guaranteeNumber()), messages);
    }

    /** «انصراف از درخواست» in review, «لغو اقدام» in action. */
    @PostMapping("/requests/{id}/abandon")
    public BranchResponses.Result abandon(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            HttpServletRequest http) {
        return BranchResponses.Result.from(reviews.abandon(command(jwt, id, http)), messages);
    }

    /** «تایید درخواست ابطال»: the guarantee was revoked in Farangin; the issuance request becomes REVOKED. */
    @PostMapping("/requests/{id}/complete-revocation")
    public BranchResponses.Result completeRevocation(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            HttpServletRequest http) {
        return BranchResponses.Result.from(reviews.completeRevocation(command(jwt, id, http)), messages);
    }

    /** An admin frees a request whoever holds it. */
    @PostMapping("/requests/{id}/force-release")
    @PreAuthorize("hasRole('ADMIN')")
    public BranchResponses.Result forceRelease(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody ReleaseBody body, HttpServletRequest http) {
        return BranchResponses.Result.from(reviews.forceRelease(command(jwt, id, http), body.reason()), messages);
    }

    public record RejectBody(@NotNull UUID reasonId, @NotBlank String comment) {
    }

    public record IssueBody(@NotBlank String guaranteeNumber) {
    }

    public record ReleaseBody(@NotBlank String reason) {
    }

    private static BranchCommand command(Jwt jwt, UUID requestId, HttpServletRequest http) {
        return new BranchCommand(user(jwt), requestId, http.getRemoteAddr(), http.getHeader(HttpHeaders.USER_AGENT));
    }

    static BranchUser user(Jwt jwt) {
        return new BranchUser(UUID.fromString(jwt.getClaimAsString("uid")), jwt.getSubject(),
                jwt.getClaimAsString("branch_code"), "ADMIN".equals(jwt.getClaimAsString("role")));
    }
}
