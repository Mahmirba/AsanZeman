package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.model.BranchData;
import com.sb24.asanzeman.request.domain.model.BranchSyncResult;
import com.sb24.asanzeman.request.domain.port.in.SyncBranchesUseCase;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.BranchSourcePort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 4.4: the fetch (several Yaghut calls) runs outside any database transaction; only the repository's {@code apply}
 * is transactional. An empty answer or a repeated branch code is refused instead of applied, because an empty list
 * would deactivate every branch.
 */
@Service
public class BranchSyncService implements SyncBranchesUseCase {

    static final String SERVICE = "Yaghut";
    private static final Logger log = LoggerFactory.getLogger(BranchSyncService.class);

    private final BranchSourcePort source;
    private final BranchRepositoryPort branches;
    private final RecordAuditUseCase audit;
    private final Clock clock;

    public BranchSyncService(BranchSourcePort source, BranchRepositoryPort branches, RecordAuditUseCase audit,
            Clock clock) {
        this.source = source;
        this.branches = branches;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public BranchSyncResult sync() {
        List<BranchData> fetched = source.fetchAll();
        check(fetched);
        BranchSyncResult result = branches.apply(fetched, clock.instant());
        log.info("Branch sync: {}", result.summary());
        audit.record(AuditEntry.system("BRANCH", null, "SYNC", result.summary()));
        return result;
    }

    @Override
    public boolean hasBranches() {
        return branches.any();
    }

    private static void check(List<BranchData> fetched) {
        if (fetched.isEmpty()) {
            throw refused("Yaghut returned no branches");
        }
        Set<String> seen = new HashSet<>();
        for (BranchData branch : fetched) {
            if (!seen.add(branch.code())) {
                throw refused("Yaghut returned branch " + branch.code() + " twice");
            }
        }
    }

    private static TemporarilyUnavailableException refused(String why) {
        log.error("Branch sync refused, nothing changed: {}", why);
        return new TemporarilyUnavailableException(SERVICE, new IllegalStateException(why));
    }
}
