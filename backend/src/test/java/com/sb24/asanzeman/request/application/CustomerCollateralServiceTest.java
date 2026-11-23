package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunUseCase;
import com.sb24.asanzeman.request.domain.model.CollateralDeposit;
import com.sb24.asanzeman.request.domain.model.CollateralKind;
import com.sb24.asanzeman.request.domain.model.CollateralPlan;
import com.sb24.asanzeman.request.domain.model.CollateralPolicy;
import com.sb24.asanzeman.request.domain.model.CustomerDeposit;
import com.sb24.asanzeman.request.domain.model.DepositRules;
import com.sb24.asanzeman.request.domain.model.InsufficientBalanceException;
import com.sb24.asanzeman.request.domain.model.InvalidCollateralException;
import com.sb24.asanzeman.request.domain.model.PriceClass;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestNotSubmittableException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestTest;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.port.in.ChooseCollateralCommand;
import com.sb24.asanzeman.request.domain.port.out.CollateralRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.DepositSourcePort;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.request.domain.service.CollateralCalculator;
import com.sb24.asanzeman.request.domain.service.DepositEligibility;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.Rials;
import com.sb24.asanzeman.support.MutableClock;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class CustomerCollateralServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final UUID SESSION = UUID.randomUUID();
    private static final DepositRules RULES = new DepositRules("IRR", Set.of("INDIVIDUAL"),
            Map.of("SHORT_ACCOUNT", PriceClass.CHEAP, "LONG_ACCOUNT", PriceClass.EXPENSIVE), Map.of());

    @Mock
    private RequestRepositoryPort requests;
    @Mock
    private InquiryRunUseCase inquiries;
    @Mock
    private DepositSourcePort source;
    @Mock
    private CollateralRepositoryPort collateral;
    @Mock
    private RecordAuditUseCase audit;

    private CustomerCollateralService service;

    @BeforeEach
    void setUp() {
        service = new CustomerCollateralService(requests, inquiries, source, new DepositEligibility(RULES),
                new CollateralCalculator(new CollateralPolicy(20, 10, new BigDecimal("1.00"), new BigDecimal("1.05"),
                        95)), collateral, audit, new MutableClock(NOW));
    }

    private static Request request(RequestStatus status) {
        return new Request(UUID.randomUUID(), RequestTest.CODE, RequestType.ISSUANCE, status, UUID.randomUUID(),
                "0123", Rials.of(1_000_000_000L), LocalDate.of(2027, 1, 20),
                RequestTest.customer(LocalDate.of(1990, 5, 17), "ACTUAL"), List.of(), "Sky Travel", NOW);
    }

    private static CustomerDeposit raw(String number, String group, String balance) {
        return new CustomerDeposit(number, group, 1L, "INDIVIDUAL", "OWNER_OF_DEPOSIT_AND_SIGNATURE", "OPEN", "IRR",
                "ACTUAL", new BigDecimal(balance));
    }

    private Request readyRequest() {
        Request request = request(RequestStatus.REGISTERED_AT_AGENCY);
        when(requests.findById(request.id())).thenReturn(Optional.of(request));
        when(inquiries.cleared(request.id())).thenReturn(true);
        return request;
    }

    private void offers(Request request, CustomerDeposit... deposits) {
        when(source.deposits(SESSION, request.customer().cif())).thenReturn(List.of(deposits));
    }

    // ---- the deposits ---------------------------------------------------------------------------------------

    @Test
    void onlyTheEligibleDepositsAreOffered() {
        Request request = readyRequest();
        offers(request, raw("D1", "SHORT_ACCOUNT", "5000000000"), raw("D2", "OTHERS", "5000000000"));

        assertThat(service.deposits(request.customer().code(), SESSION, request.id()))
                .extracting(CollateralDeposit::number).containsExactly("D1");
    }

    @Test
    void anotherCustomersRequestIsNotFoundAndNothingIsAsked() {
        Request request = request(RequestStatus.REGISTERED_AT_AGENCY);
        when(requests.findById(request.id())).thenReturn(Optional.of(request));
        UUID unknown = UUID.randomUUID();
        when(requests.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deposits("1000000028", SESSION, request.id()))
                .isInstanceOf(RequestNotFoundException.class);
        assertThatThrownBy(() -> service.deposits(request.customer().code(), SESSION, unknown))
                .isInstanceOf(RequestNotFoundException.class);
        verifyNoInteractions(source, inquiries);
    }

    @Test
    void aRequestNotAtTheAgencyStepOrWithoutPassedInquiriesCannotGoOn() {
        Request rejected = request(RequestStatus.REJECTED);
        when(requests.findById(rejected.id())).thenReturn(Optional.of(rejected));
        Request waiting = request(RequestStatus.REGISTERED_AT_AGENCY);
        when(requests.findById(waiting.id())).thenReturn(Optional.of(waiting));
        when(inquiries.cleared(waiting.id())).thenReturn(false);

        assertThatThrownBy(() -> service.deposits(rejected.customer().code(), SESSION, rejected.id()))
                .isInstanceOf(RequestNotSubmittableException.class).hasMessageContaining("REJECTED");
        assertThatThrownBy(() -> service.deposits(waiting.customer().code(), SESSION, waiting.id()))
                .isInstanceOf(RequestNotSubmittableException.class).hasMessageContaining("inquiries");
        verifyNoInteractions(source);
    }

    // ---- choosing -------------------------------------------------------------------------------------------

    @Test
    void theChoiceIsCalculatedFromTheBalancesAsTheyAreNowAndSaved() {
        Request request = readyRequest();
        offers(request, raw("D1", "SHORT_ACCOUNT", "5000000000"), raw("D3", "LONG_ACCOUNT", "3000000000"));

        CollateralPlan plan = service.choose(request.customer().code(), SESSION, request.id(),
                new ChooseCollateralCommand(20, List.of("D1"), List.of("D3")));

        assertThat(plan.cash()).isEqualTo(Rials.of(200_000_000L));
        assertThat(plan.totalCollateral()).isEqualTo(Rials.of(1_040_000_000L));
        verify(collateral).replace(request.id(), plan, NOW);
    }

    @Test
    void aDepositThatIsNotOfferedCannotBeChosen() {
        Request request = readyRequest();
        offers(request, raw("D1", "SHORT_ACCOUNT", "5000000000"), raw("D9", "OTHERS", "5000000000"));

        assertThatThrownBy(() -> service.choose(request.customer().code(), SESSION, request.id(),
                new ChooseCollateralCommand(100, List.of("D9"), null))).isInstanceOf(InvalidCollateralException.class)
                .hasMessageContaining("D9");
        assertThatThrownBy(() -> service.choose(request.customer().code(), SESSION, request.id(),
                new ChooseCollateralCommand(100, List.of("NOPE"), List.of()))).isInstanceOf(
                        InvalidCollateralException.class);
        verify(collateral, never()).replace(any(), any(), any());
    }

    @Test
    void aChoiceTheBalancesCannotCoverIsNotSaved() {
        Request request = readyRequest();
        offers(request, raw("D1", "SHORT_ACCOUNT", "10"));

        assertThatThrownBy(() -> service.choose(request.customer().code(), SESSION, request.id(),
                new ChooseCollateralCommand(100, List.of("D1"), List.of())))
                .isInstanceOf(InsufficientBalanceException.class);
        verify(collateral, never()).replace(any(), any(), any());
    }

    @Test
    void theCommandKeepsItsListsAndTreatsMissingOnesAsEmpty() {
        var cash = new java.util.ArrayList<>(List.of("D1"));
        var command = new ChooseCollateralCommand(100, cash, null);
        cash.clear();

        assertThat(command.cashDeposits()).containsExactly("D1");
        assertThat(command.pledgeDeposits()).isEmpty();
        assertThat(new ChooseCollateralCommand(100, null, List.of("D2")).cashDeposits()).isEmpty();
    }

    // ---- submitting -----------------------------------------------------------------------------------------

    @Test
    void submittingSendsTheRequestToTheBranchUnderTheRowLockAndIsAudited() {
        Request request = readyRequest();
        when(collateral.exists(request.id())).thenReturn(true);
        when(requests.findByIdForUpdate(request.id())).thenReturn(Optional.of(request));

        RequestStatus status = service.submit(request.customer().code(), request.id());

        assertThat(status).isEqualTo(RequestStatus.PENDING_BRANCH_REVIEW);
        verify(requests).updateStatus(request.id(), RequestStatus.PENDING_BRANCH_REVIEW, NOW);
        var entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::action, AuditEntry::userRole, AuditEntry::userName)
                .containsExactly("SUBMIT", "CUSTOMER", request.customer().code());
    }

    @Test
    void withoutAChosenCollateralTheRequestCannotBeSubmitted() {
        Request request = readyRequest();
        when(collateral.exists(request.id())).thenReturn(false);

        assertThatThrownBy(() -> service.submit(request.customer().code(), request.id()))
                .isInstanceOf(RequestNotSubmittableException.class).hasMessageContaining("collateral");
        verify(requests, never()).updateStatus(any(), any(), any());
    }

    @Test
    void aRequestThatDisappearedUnderTheLockIsNotFound() {
        Request request = readyRequest();
        when(collateral.exists(request.id())).thenReturn(true);
        when(requests.findByIdForUpdate(request.id())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(request.customer().code(), request.id()))
                .isInstanceOf(RequestNotFoundException.class);
    }

    @Test
    void theSubmitRunsInOneTransactionButTheYaghutCallsDoNot() throws Exception {
        assertThat(CustomerCollateralService.class.getMethod("submit", String.class, UUID.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(CustomerCollateralService.class.getAnnotation(Transactional.class)).isNull();
        assertThat(CollateralKind.valueOf("CASH")).isNotNull();
    }
}
