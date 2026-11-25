package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestBusyException;
import com.sb24.asanzeman.request.domain.port.in.ExpireIncompleteRequestsUseCase;
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerNotifierPort;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The 7-day rule (4.2): an issuance request still at the agency step {@code window} after it was registered is rejected.
 * Each request is read under the row lock and checked again, so one the customer completed or the agency cancelled a
 * moment ago is left alone, and a busy one waits for the next run. Each request is its own transaction (the status, the
 * audit entry and the customer SMS together), so one that fails is logged and the others still go on.
 */
@Service
public class ExpireIncompleteRequestsService implements ExpireIncompleteRequestsUseCase {

    static final String ENTITY = "REQUEST";
    private static final Logger log = LoggerFactory.getLogger(ExpireIncompleteRequestsService.class);

    private final RequestRepositoryPort requests;
    private final AgencyRegistryPort agencies;
    private final CustomerNotifierPort notifier;
    private final RecordAuditUseCase audit;
    private final Clock clock;
    private final CompletionSettings settings;
    private final TransactionTemplate transaction;

    public ExpireIncompleteRequestsService(RequestRepositoryPort requests, AgencyRegistryPort agencies,
            CustomerNotifierPort notifier, RecordAuditUseCase audit, Clock clock, CompletionSettings settings,
            PlatformTransactionManager transactions) {
        this.requests = requests;
        this.agencies = agencies;
        this.notifier = notifier;
        this.audit = audit;
        this.clock = clock;
        this.settings = settings;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Override
    public int expireIncomplete() {
        Instant cutoff = clock.instant().minus(settings.window());
        int rejected = 0;
        for (UUID id : requests.incompleteSince(cutoff)) {
            try {
                if (Boolean.TRUE.equals(transaction.execute(status -> expireOne(id)))) {
                    rejected++;
                }
            } catch (RequestBusyException e) {
                log.info("request {} is busy, its 7-day rejection is left for the next run", id);
            } catch (RuntimeException e) {
                log.error("request {} could not be rejected, the others go on", id, e);
            }
        }
        return rejected;
    }

    /** One request in its own transaction, so a failure rolls back only that request. */
    private boolean expireOne(UUID id) {
        Request request = requests.findByIdForUpdate(id).filter(Request::canBeInquired).orElse(null);
        if (request == null) {
            return false;
        }
        reject(request, clock.instant());
        return true;
    }

    private void reject(Request request, Instant now) {
        Request rejected = request.expired();
        requests.updateStatus(request.id(), rejected.status(), now);
        audit.record(new AuditEntry(null, "system", "SYSTEM", ENTITY, request.id(), "AUTO_REJECT",
                request.status().name(), rejected.status().name(), "not completed within " + settings.window(), null,
                null, null));
        String agencyName = agencies.find(request.agencyId()).map(a -> a.name()).orElse("");
        notifier.requestExpired(request.id(), request.customer().mobile(), agencyName,
                JalaliDate.ofInstant(now).toSlashed());
    }
}
