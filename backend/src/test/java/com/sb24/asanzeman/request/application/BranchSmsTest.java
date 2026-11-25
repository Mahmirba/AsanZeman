package com.sb24.asanzeman.request.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.inquiry.domain.model.Gender;
import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunUseCase;
import com.sb24.asanzeman.request.domain.model.AgencyInfo;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestTest;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.model.TrackingCode;
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort;
import com.sb24.asanzeman.shared.model.JalaliDate;
import com.sb24.asanzeman.shared.model.Rials;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** The words of the branch's SMS messages: who is named, which date, which salutation, and who else is told. */
@ExtendWith(MockitoExtension.class)
class BranchSmsTest {

    // 2026-11-23 10:00 UTC is 13:30 in Tehran: 1405/09/02
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final String TODAY = "1405/09/02";
    private static final UUID AGENCY = UUID.randomUUID();
    private static final TrackingCode REVOCATION_CODE = TrackingCode.of("0123", "0042", JalaliDate.of(1405, 9, 2), 9);

    @Mock
    private CustomerNotifierPort notifier;
    @Mock
    private AgencyRegistryPort agencies;
    @Mock
    private BranchRepositoryPort branches;
    @Mock
    private InquiryRunUseCase inquiries;

    private BranchSms sms;

    @BeforeEach
    void setUp() {
        sms = new BranchSms(notifier, agencies, branches, inquiries, new MutableClock(NOW));
    }

    private static Request issuance(RequestStatus status) {
        return new Request(UUID.randomUUID(), RequestTest.CODE, RequestType.ISSUANCE, status, AGENCY, "0123",
                Rials.of(1000), LocalDate.of(2027, 1, 20), RequestTest.customer(LocalDate.of(1990, 5, 17), "ACTUAL"),
                List.of(), "Sky Travel", NOW);
    }

    private static Request revocationOf(Request issuance) {
        return issuance.requestRevocation(UUID.randomUUID(), REVOCATION_CODE, RequestStatus.UNDER_ACTION, "Sky Travel",
                NOW);
    }

    private void agencyIs(String name, String mobile) {
        when(agencies.find(AGENCY)).thenReturn(Optional.of(new AgencyInfo(AGENCY, "0042", name, true, mobile)));
    }

    private void branchIs(String name) {
        when(branches.nameOf("0123")).thenReturn(Optional.ofNullable(name));
    }

    @Test
    void anIssuedGuaranteeNamesTheAgencyTheBranchAndTheDateAndAddressesTheCustomerByGender() {
        Request issued = issuance(RequestStatus.ISSUED);
        agencyIs("Sky Travel", null);
        branchIs("Valiasr");
        when(inquiries.genderOf(issued.id())).thenReturn(Optional.of(Gender.MALE));

        sms.issued(issued);

        verify(notifier).guaranteeIssued(issued.id(), "09121234567", "Sky Travel", "Valiasr", TODAY,
                "آقا Ali Rezaei\n");
    }

    @Test
    void aRejectedRequestWithNoKnownGenderIsAddressedWithoutASalutation() {
        Request rejected = issuance(RequestStatus.REJECTED);
        agencyIs("Sky Travel", null);
        branchIs("Valiasr");
        when(inquiries.genderOf(rejected.id())).thenReturn(Optional.empty());

        sms.issuanceRejected(rejected);

        verify(notifier).requestRejected(rejected.id(), "09121234567", "Sky Travel", "Valiasr", TODAY, "");
    }

    @Test
    void aMissingAgencyIsNamedWithNothingAndAMissingBranchByItsCode() {
        Request rejected = issuance(RequestStatus.REJECTED);
        when(agencies.find(AGENCY)).thenReturn(Optional.empty());
        branchIs(null);
        when(inquiries.genderOf(rejected.id())).thenReturn(Optional.of(Gender.FEMALE));

        sms.issuanceRejected(rejected);

        verify(notifier).requestRejected(rejected.id(), "09121234567", "", "0123", TODAY, "خانم Ali Rezaei\n");
    }

    @Test
    void aRejectedRevocationTellsTheCustomerByTheGenderOfTheIssuanceAndTheAgencyByItsOwnMobile() {
        Request issuance = issuance(RequestStatus.ISSUED);
        Request revocation = revocationOf(issuance);
        agencyIs("Sky Travel", "09129990000");
        branchIs("Valiasr");
        when(inquiries.genderOf(issuance.id())).thenReturn(Optional.of(Gender.FEMALE));

        sms.revocationRejected(revocation, issuance.id());

        verify(notifier).revocationRejected(revocation.id(), "09121234567", "Sky Travel", "Valiasr", TODAY,
                "خانم Ali Rezaei\n");
        verify(notifier).agencyRevocationRejected(revocation.id(), "09129990000", REVOCATION_CODE.value(), "Valiasr",
                TODAY);
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void aCompletedRevocationTellsTheCustomerAboutTheIssuanceAndTheAgencyAboutItsRevocation() {
        Request issuance = issuance(RequestStatus.ISSUED);
        Request revocation = revocationOf(issuance);
        agencyIs("Sky Travel", "09129990000");
        branchIs("Valiasr");
        when(inquiries.genderOf(issuance.id())).thenReturn(Optional.of(Gender.MALE));

        sms.revoked(issuance, revocation);

        verify(notifier).guaranteeRevoked(issuance.id(), "09121234567", "Sky Travel", "Valiasr", TODAY,
                "آقا Ali Rezaei\n");
        verify(notifier).agencyRevocationCompleted(revocation.id(), "09129990000", REVOCATION_CODE.value(), "Valiasr",
                TODAY);
        verifyNoMoreInteractions(notifier);
    }

    @Test
    void anAgencyWithoutAMobileNumberIsPassedAsNoNumber() {
        Request issuance = issuance(RequestStatus.ISSUED);
        Request revocation = revocationOf(issuance);
        agencyIs("Sky Travel", null);
        branchIs("Valiasr");
        when(inquiries.genderOf(issuance.id())).thenReturn(Optional.empty());

        sms.revoked(issuance, revocation);

        verify(notifier).agencyRevocationCompleted(revocation.id(), null, REVOCATION_CODE.value(), "Valiasr", TODAY);
    }
}
