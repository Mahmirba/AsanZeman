package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.model.CancelOutcome;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestCommand;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestResult;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestUseCase;
import com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4.2: the agency's one action, decided under the row lock. The request is read with {@code FOR UPDATE NOWAIT}, so
 * if a branch user takes it at the same moment, one of the two goes first: if the branch wins, the agency sees the
 * new status and gets the revocation answer instead; if the agency wins, the branch user is refused.
 *
 * <p>Only the cancel outcome exists so far. A request the branch has started on needs a revocation request, which
 * comes with the branch side (Phase 4 and 5), so the domain refuses it with a clear 409 for now.
 */
@Service
@Transactional
public class CancelRequestService implements CancelRequestUseCase {

    static final String ENTITY = "REQUEST";

    private final RequestRepositoryPort requests;
    private final CustomerNotifierPort notifier;
    private final RecordAuditUseCase audit;
    private final Clock clock;

    public CancelRequestService(RequestRepositoryPort requests, CustomerNotifierPort notifier,
            RecordAuditUseCase audit, Clock clock) {
        this.requests = requests;
        this.notifier = notifier;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public CancelRequestResult cancel(CancelRequestCommand command) {
        Request request = requests.findByIdForUpdate(command.requestId())
                .filter(r -> r.agencyId().equals(command.agencyId()))
                .orElseThrow(RequestNotFoundException::new);
        Request cancelled = request.cancelByAgency();
        Instant now = clock.instant();
        requests.updateStatus(request.id(), cancelled.status(), now);
        String sentDate = JalaliDate.fromGregorian(now.atZone(TrackingCodeGenerator.TEHRAN).toLocalDate())
                .toSlashed();
        notifier.requestCancelled(request.id(), request.customer().mobile(), command.agencyName(), sentDate);
        audit.record(new AuditEntry(command.agencyId(), command.agencyName(), "AGENCY", ENTITY, request.id(),
                "CANCEL", request.status().name(), cancelled.status().name(), null, command.clientIp(),
                command.userAgent(), null));
        return new CancelRequestResult(request.id(), CancelOutcome.CANCELLED, cancelled.status());
    }
}
