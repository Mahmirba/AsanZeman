package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunUseCase;
import com.sb24.asanzeman.request.domain.model.CollateralDeposit;
import com.sb24.asanzeman.request.domain.model.CollateralPlan;
import com.sb24.asanzeman.request.domain.model.InvalidCollateralException;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestNotSubmittableException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.port.in.ChooseCollateralCommand;
import com.sb24.asanzeman.request.domain.port.in.CustomerCollateralUseCase;
import com.sb24.asanzeman.request.domain.port.out.CollateralRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.DepositSourcePort;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.request.domain.service.CollateralCalculator;
import com.sb24.asanzeman.request.domain.service.DepositEligibility;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The customer's last steps (4.3): offer the deposits, calculate and save the collateral, submit. All three need the
 * request to be the customer's own, still at the agency's step, with every inquiry passed. The Yaghut call that reads
 * the balances runs outside any database transaction; the saving of the choice and the submit each run in one.
 * Balances are read when the customer chooses and are never saved (D16).
 */
@Service
public class CustomerCollateralService implements CustomerCollateralUseCase {

    static final String ENTITY = "REQUEST";

    private final RequestRepositoryPort requests;
    private final InquiryRunUseCase inquiries;
    private final DepositSourcePort source;
    private final DepositEligibility eligibility;
    private final CollateralCalculator calculator;
    private final CollateralRepositoryPort collateral;
    private final RecordAuditUseCase audit;
    private final Clock clock;

    public CustomerCollateralService(RequestRepositoryPort requests, InquiryRunUseCase inquiries,
            DepositSourcePort source, DepositEligibility eligibility, CollateralCalculator calculator,
            CollateralRepositoryPort collateral, RecordAuditUseCase audit, Clock clock) {
        this.requests = requests;
        this.inquiries = inquiries;
        this.source = source;
        this.eligibility = eligibility;
        this.calculator = calculator;
        this.collateral = collateral;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public List<CollateralDeposit> deposits(String nationalCode, UUID sessionId, UUID requestId) {
        return offered(sessionId, ready(nationalCode, requestId));
    }

    @Override
    public CollateralPlan choose(String nationalCode, UUID sessionId, UUID requestId,
            ChooseCollateralCommand command) {
        Request request = ready(nationalCode, requestId);
        Map<String, CollateralDeposit> offered = new HashMap<>();
        offered(sessionId, request).forEach(d -> offered.put(d.number(), d));
        CollateralPlan plan = calculator.plan(request.amount(), command.cashPercent(),
                pick(command.cashDeposits(), offered), pick(command.pledgeDeposits(), offered));
        collateral.replace(request.id(), plan, clock.instant());
        return plan;
    }

    @Override
    @Transactional
    public RequestStatus submit(String nationalCode, UUID requestId) {
        Request request = ready(nationalCode, requestId);
        if (!collateral.exists(request.id())) {
            throw new RequestNotSubmittableException("no collateral was chosen");
        }
        // decided under the row lock, like every change to a request (4.1)
        Request locked = requests.findByIdForUpdate(request.id()).orElseThrow(RequestNotFoundException::new);
        Request submitted = locked.submitted();
        requests.updateStatus(locked.id(), submitted.status(), clock.instant());
        audit.record(new AuditEntry(null, nationalCode, "CUSTOMER", ENTITY, locked.id(), "SUBMIT",
                locked.status().name(), submitted.status().name(), null, null, null, null));
        return submitted.status();
    }

    /** The customer's own request, still at the agency's step, whose inquiries have all passed. */
    private Request ready(String nationalCode, UUID requestId) {
        Request request = requests.findById(requestId).filter(r -> r.customer().code().equals(nationalCode))
                .orElseThrow(RequestNotFoundException::new);
        if (!request.canBeInquired()) {
            throw new RequestNotSubmittableException("the request is " + request.status());
        }
        if (!inquiries.cleared(request.id())) {
            throw new RequestNotSubmittableException("the inquiries have not passed");
        }
        return request;
    }

    private List<CollateralDeposit> offered(UUID sessionId, Request request) {
        return eligibility.eligible(source.deposits(sessionId, request.customer().cif()));
    }

    private static List<CollateralDeposit> pick(List<String> numbers, Map<String, CollateralDeposit> offered) {
        return numbers.stream().map(number -> {
            CollateralDeposit deposit = offered.get(number);
            if (deposit == null) {
                throw new InvalidCollateralException("deposit " + number + " cannot be offered");
            }
            return deposit;
        }).toList();
    }
}
