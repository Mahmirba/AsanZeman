package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunUseCase;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort;
import com.sb24.asanzeman.request.domain.service.Salutation;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The SMS messages the branch's decisions send (6.4): to the customer, with the agency, the branch, the date and the
 * salutation filled in, and to the agency about a revocation. It only prepares and queues them, in the caller's
 * transaction, so a message is sent only if the decision commits.
 */
@Service
class BranchSms {

    private final CustomerNotifierPort notifier;
    private final AgencyRegistryPort agencies;
    private final BranchRepositoryPort branches;
    private final InquiryRunUseCase inquiries;
    private final Clock clock;

    BranchSms(CustomerNotifierPort notifier, AgencyRegistryPort agencies, BranchRepositoryPort branches,
            InquiryRunUseCase inquiries, Clock clock) {
        this.notifier = notifier;
        this.agencies = agencies;
        this.branches = branches;
        this.inquiries = inquiries;
        this.clock = clock;
    }

    /** The branch recorded the guarantee number: the customer is told the guarantee is issued. */
    void issued(Request issuance) {
        notifier.guaranteeIssued(issuance.id(), issuance.customer().mobile(), agencyName(issuance),
                branchName(issuance), today(), salutation(issuance, issuance.id()));
    }

    /** The branch rejected an issuance request. */
    void issuanceRejected(Request issuance) {
        notifier.requestRejected(issuance.id(), issuance.customer().mobile(), agencyName(issuance),
                branchName(issuance), today(), salutation(issuance, issuance.id()));
    }

    /** The branch did not approve a revocation request: the customer and the agency are told. */
    void revocationRejected(Request revocation, UUID issuanceId) {
        notifier.revocationRejected(revocation.id(), revocation.customer().mobile(), agencyName(revocation),
                branchName(revocation), today(), salutation(revocation, issuanceId));
        notifier.agencyRevocationRejected(revocation.id(), agencyMobile(revocation), revocation.trackingCode().value(),
                branchName(revocation), today());
    }

    /** The guarantee was revoked: the customer is told about the issuance request, the agency about its revocation. */
    void revoked(Request issuance, Request revocation) {
        notifier.guaranteeRevoked(issuance.id(), issuance.customer().mobile(), agencyName(issuance),
                branchName(issuance), today(), salutation(issuance, issuance.id()));
        notifier.agencyRevocationCompleted(revocation.id(), agencyMobile(revocation), revocation.trackingCode().value(),
                branchName(revocation), today());
    }

    private String agencyName(Request request) {
        return agencies.find(request.agencyId()).map(a -> a.name()).orElse("");
    }

    private String agencyMobile(Request request) {
        return agencies.find(request.agencyId()).map(a -> a.mobile()).orElse(null);
    }

    private String branchName(Request request) {
        return branches.nameOf(request.branchCode()).orElse(request.branchCode());
    }

    /** The customer's gender is from the issuance request's inquiries; a revocation request has none of its own. */
    private String salutation(Request request, UUID issuanceId) {
        String gender = inquiries.genderOf(issuanceId).map(Enum::name).orElse(null);
        return Salutation.line(gender, request.customer().fullName());
    }

    private String today() {
        return JalaliDate.ofInstant(clock.instant()).toSlashed();
    }
}
