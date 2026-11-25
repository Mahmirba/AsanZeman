package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.model.AgencyInfo;
import com.sb24.asanzeman.request.domain.model.AgencyNotActiveException;
import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.CustomerSnapshot;
import com.sb24.asanzeman.request.domain.model.DuplicateRegistrationException;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.TrackingCode;
import com.sb24.asanzeman.request.domain.port.in.RegisterRequestCommand;
import com.sb24.asanzeman.request.domain.port.in.RegisteredRequest;
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.JalaliDate;
import com.sb24.asanzeman.shared.model.Rials;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database half of registration (D47, D12): everything here is one transaction. The agency row is locked first,
 * so two simultaneous registrations by one agency run one after the other and the second sees the first. The
 * tracking number, the request, the SMS and the audit row commit together or not at all.
 *
 * <p>It is a separate bean so the Yaghut call before it stays outside the transaction.
 */
@Service
public class RequestRegistrar {

    static final String ENTITY = "REQUEST";

    private final AgencyRegistryPort agencies;
    private final RequestRepositoryPort requests;
    private final TrackingCodeGenerator trackingCodes;
    private final CustomerNotifierPort notifier;
    private final RecordAuditUseCase audit;
    private final Clock clock;
    private final RegistrationSettings settings;

    public RequestRegistrar(AgencyRegistryPort agencies, RequestRepositoryPort requests,
            TrackingCodeGenerator trackingCodes, CustomerNotifierPort notifier, RecordAuditUseCase audit,
            Clock clock, RegistrationSettings settings) {
        this.agencies = agencies;
        this.requests = requests;
        this.trackingCodes = trackingCodes;
        this.notifier = notifier;
        this.audit = audit;
        this.clock = clock;
        this.settings = settings;
    }

    @Transactional
    public RegisteredRequest persist(RegisterRequestCommand command, CustomerSnapshot customer,
            List<Companion> companions) {
        if (command.amount() < 0) {
            throw new InvalidRequestDataException("the amount must not be negative");
        }
        AgencyInfo agency = agencies.lock(command.agencyId()).orElseThrow(AgencyNotActiveException::new);
        if (!agency.active()) {
            throw new AgencyNotActiveException();
        }
        Instant now = clock.instant();
        if (requests.existsSince(agency.id(), customer.code(), now.minus(settings.duplicateWindow()))) {
            throw new DuplicateRegistrationException();
        }
        LocalDate today = now.atZone(JalaliDate.TEHRAN).toLocalDate();
        TrackingCode trackingCode = trackingCodes.next(command.branchCode(), agency.code());
        Request request = Request.register(UUID.randomUUID(), trackingCode, agency.id(), command.branchCode(),
                Rials.of(command.amount()), command.dueDate(), customer, companions, command.registeredBy(), now,
                today);
        requests.insert(request);
        notifier.customerRegistered(request.id(), customer.mobile(), agency.name());
        audit.record(new AuditEntry(agency.id(), command.registeredBy(), "AGENCY", ENTITY, request.id(), "REGISTER",
                null, request.status().name(), null, command.clientIp(), command.userAgent(), null));
        return new RegisteredRequest(request.id(), trackingCode.value(), request.status());
    }
}
