package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.model.BranchNotAvailableException;
import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.CustomerSnapshot;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.port.in.RegisterRequestCommand;
import com.sb24.asanzeman.request.domain.port.in.RegisterRequestUseCase;
import com.sb24.asanzeman.request.domain.port.in.RegisteredRequest;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerLookupPort;
import com.sb24.asanzeman.shared.model.NationalCode;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * SRS UC-AZS-8: checks that need no I/O come first, then the branch (a table read), then the customer from Yaghut
 * with the agency's own session. None of this is in a database transaction: the Yaghut call can take seconds and a
 * transaction must not wait for it. {@link RequestRegistrar} then does the transactional part.
 */
@Service
public class RegisterRequestService implements RegisterRequestUseCase {

    private final BranchRepositoryPort branches;
    private final CustomerLookupPort customers;
    private final RequestRegistrar registrar;

    public RegisterRequestService(BranchRepositoryPort branches, CustomerLookupPort customers,
            RequestRegistrar registrar) {
        this.branches = branches;
        this.customers = customers;
        this.registrar = registrar;
    }

    @Override
    public RegisteredRequest register(RegisterRequestCommand command) {
        NationalCode code = NationalCode.parse(command.nationalCode())
                .orElseThrow(() -> new InvalidRequestDataException("the customer national code is not valid"));
        List<Companion> companions = command.companions().stream().map(c -> new Companion(c.fullName(),
                NationalCode.normalise(c.nationalCode()), c.birthDate(), c.passportNumber())).toList();
        if (!branches.isOpen(command.branchCode())) {
            throw new BranchNotAvailableException();
        }
        CustomerSnapshot customer = customers.find(command.sessionId(), code.value());
        if (!code.value().equals(customer.code())) {
            // never register a request for a customer other than the one the agency asked about
            throw new TemporarilyUnavailableException("Yaghut", new IllegalStateException(
                    "customerDetailInfo answered for a different national code"));
        }
        return registrar.persist(command, customer, companions);
    }
}
