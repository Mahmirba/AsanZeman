package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.BranchSummary;
import com.sb24.asanzeman.request.domain.port.in.AgencyProfile;
import com.sb24.asanzeman.request.domain.port.in.AgencyProfileUseCase;
import com.sb24.asanzeman.request.domain.port.in.CustomerLookupResult;
import com.sb24.asanzeman.request.domain.port.in.ListBranchesUseCase;
import com.sb24.asanzeman.request.domain.port.in.LookupCustomerUseCase;
import com.sb24.asanzeman.shared.model.JalaliDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The small calls behind the agency panel's pages: who is logged in, the branch list and the customer search of the
 * registration form (SRS UC-AZS-8). AGENCY sessions only.
 */
@RestController
@RequestMapping("/api/agency")
@PreAuthorize("hasRole('AGENCY')")
public class AgencyPanelController {

    /** Body of the customer search: the national code the agency typed. */
    public record LookupBody(@NotBlank String nationalCode) {
    }

    /** The customer as the form shows it; the birth date is Jalali {@code yyyy/MM/dd}. */
    public record CustomerResponse(String cif, String nationalCode, String fullName, String birthDate,
            String mobile) {
    }

    private final AgencyProfileUseCase profile;
    private final ListBranchesUseCase branches;
    private final LookupCustomerUseCase lookup;

    public AgencyPanelController(AgencyProfileUseCase profile, ListBranchesUseCase branches,
            LookupCustomerUseCase lookup) {
        this.profile = profile;
        this.branches = branches;
        this.lookup = lookup;
    }

    /** The agency name for the top of every page (SRS UC-AZS-13). */
    @GetMapping("/profile")
    public AgencyProfile profile(@AuthenticationPrincipal Jwt jwt) {
        return profile.profile(AgencyRequestController.agency(jwt));
    }

    /** Open branches for the cascading list; {@code q} is the first letters of the name or code. */
    @GetMapping("/branches")
    public List<BranchSummary> branches(@RequestParam(required = false) String q) {
        return branches.listOpen(q);
    }

    /** The «جستجو» step of the registration form: the customer, checked for being a bank customer and 18 or older. */
    @PostMapping("/customers/lookup")
    public CustomerResponse lookup(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody LookupBody body) {
        CustomerLookupResult c = lookup.lookup(AgencyRequestController.session(jwt), body.nationalCode());
        return new CustomerResponse(c.cif(), c.nationalCode(), c.fullName(),
                c.birthDate() == null ? null : JalaliDate.fromGregorian(c.birthDate()).toSlashed(), c.mobile());
    }
}
