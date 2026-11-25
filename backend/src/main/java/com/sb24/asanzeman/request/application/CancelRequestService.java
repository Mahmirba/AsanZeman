package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.model.AgencyInfo;
import com.sb24.asanzeman.request.domain.model.AgencyNotActiveException;
import com.sb24.asanzeman.request.domain.model.CancelOutcome;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.Review;
import com.sb24.asanzeman.request.domain.model.ReviewLock;
import com.sb24.asanzeman.request.domain.model.RevocationAlreadyRequestedException;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestCommand;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestResult;
import com.sb24.asanzeman.request.domain.port.in.CancelRequestUseCase;
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.ReviewRepositoryPort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 4.2: the agency's one action, decided under the row lock. The request is read with {@code FOR UPDATE NOWAIT}, so
 * if a branch user takes it at the same moment, one of the two goes first: if the branch wins, the agency sees the
 * new status and gets a revocation request instead; if the agency wins, the branch user is refused.
 *
 * <ul>
 *   <li>Before «اقدام» (registered, or waiting in the branch's queue): the request is cancelled at once, and the customer
 *       gets an SMS.</li>
 *   <li>From «اقدام» on (being reviewed, in action, or issued): a revocation request is created. It waits in the branch's
 *       queue, or, when the issuance request is mid-work, goes straight to the user who holds it (D17). A second
 *       revocation while one is open is refused.</li>
 *   <li>Anything finished, and a revocation request itself, has nothing to cancel: 409.</li>
 * </ul>
 */
@Service
@Transactional
public class CancelRequestService implements CancelRequestUseCase {

    static final String ENTITY = "REQUEST";

    private final ReviewRepositoryPort reviews;
    private final RequestRepositoryPort requests;
    private final AgencyRegistryPort agencies;
    private final TrackingCodeGenerator trackingCodes;
    private final CustomerNotifierPort notifier;
    private final RecordAuditUseCase audit;
    private final Clock clock;

    public CancelRequestService(ReviewRepositoryPort reviews, RequestRepositoryPort requests,
            AgencyRegistryPort agencies, TrackingCodeGenerator trackingCodes, CustomerNotifierPort notifier,
            RecordAuditUseCase audit, Clock clock) {
        this.reviews = reviews;
        this.requests = requests;
        this.agencies = agencies;
        this.trackingCodes = trackingCodes;
        this.notifier = notifier;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public CancelRequestResult cancel(CancelRequestCommand command) {
        Review review = reviews.findForUpdate(command.requestId())
                .filter(r -> r.request().agencyId().equals(command.agencyId()))
                .orElseThrow(RequestNotFoundException::new);
        Request request = review.request();
        Instant now = clock.instant();
        if (request.needsRevocation()) {
            return requestRevocation(command, review, now);
        }
        Request cancelled = request.cancelByAgency();
        requests.updateStatus(request.id(), cancelled.status(), now);
        String sentDate = JalaliDate.fromGregorian(now.atZone(TrackingCodeGenerator.TEHRAN).toLocalDate())
                .toSlashed();
        notifier.requestCancelled(request.id(), request.customer().mobile(), command.agencyName(), sentDate);
        audit.record(new AuditEntry(command.agencyId(), command.actor(), "AGENCY", ENTITY, request.id(),
                "CANCEL", request.status().name(), cancelled.status().name(), null, command.clientIp(),
                command.userAgent(), null));
        return new CancelRequestResult(request.id(), CancelOutcome.CANCELLED, cancelled.status());
    }

    private CancelRequestResult requestRevocation(CancelRequestCommand command, Review review, Instant now) {
        if (review.revocationOpen()) {
            throw new RevocationAlreadyRequestedException();
        }
        Request issuance = review.request();
        AgencyInfo agency = agencies.find(command.agencyId()).filter(AgencyInfo::active)
                .orElseThrow(AgencyNotActiveException::new);
        // an issuance request somebody holds hands its revocation straight to that user (D17)
        ReviewLock holder = review.lock();
        ReviewLock lock = holder == null ? null : new ReviewLock(holder.holderId(), holder.holderName(), now, now);
        RequestStatus status = holder == null ? RequestStatus.PENDING_BRANCH_REVIEW : RequestStatus.UNDER_REVIEW;
        Request revocation = issuance.requestRevocation(UUID.randomUUID(),
                trackingCodes.next(issuance.branchCode(), agency.code()), status, command.actor(), now);
        reviews.insertRevocation(revocation, issuance.id(), lock);
        audit.record(new AuditEntry(command.agencyId(), command.actor(), "AGENCY", ENTITY, issuance.id(),
                "REQUEST_REVOCATION", issuance.status().name(), issuance.status().name(),
                "revocation " + revocation.trackingCode().value(), command.clientIp(), command.userAgent(), null));
        return new CancelRequestResult(revocation.id(), CancelOutcome.REVOCATION_REQUESTED, revocation.status());
    }
}
