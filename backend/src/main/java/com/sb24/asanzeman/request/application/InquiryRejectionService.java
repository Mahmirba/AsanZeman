package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict;
import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunFinished;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestBusyException;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import java.time.Clock;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens for the end of an inquiry run (5.1): a rejection rejects the request. The inquiry module never calls this
 * module, it only announces the result. The request is read under the row lock; if it is no longer waiting for its
 * inquiries (the agency cancelled it meanwhile) nothing changes, and if the lock cannot be had at once the rejection
 * is dropped, which is safe: the customer's next run asks again and is rejected again.
 */
@Service
public class InquiryRejectionService {

    static final String ENTITY = "REQUEST";
    private static final Logger log = LoggerFactory.getLogger(InquiryRejectionService.class);

    private final RequestRepositoryPort requests;
    private final RecordAuditUseCase audit;
    private final Clock clock;

    public InquiryRejectionService(RequestRepositoryPort requests, RecordAuditUseCase audit, Clock clock) {
        this.requests = requests;
        this.audit = audit;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void on(InquiryRunFinished event) {
        if (event.decision() != InquiryVerdict.Decision.REJECT) {
            return;
        }
        Optional<Request> found;
        try {
            found = requests.findByIdForUpdate(event.requestId());
        } catch (RequestBusyException e) {
            log.warn("request {} is busy, its rejection by the inquiries is left for the next run", event.requestId());
            return;
        }
        Request request = found.filter(Request::canBeInquired).orElse(null);
        if (request == null) {
            log.info("request {} no longer waits for its inquiries, the rejection is ignored", event.requestId());
            return;
        }
        Request rejected = request.rejectedByInquiry();
        requests.updateStatus(request.id(), rejected.status(), clock.instant());
        audit.record(new AuditEntry(null, "inquiry", "SYSTEM", ENTITY, request.id(), "REJECT_BY_INQUIRY",
                request.status().name(), rejected.status().name(), event.rejectionMessageKey(), null, null, null));
    }
}
