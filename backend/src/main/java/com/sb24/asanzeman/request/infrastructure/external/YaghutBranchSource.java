package com.sb24.asanzeman.request.infrastructure.external;

import com.sb24.asanzeman.integration.yaghut.YaghutBranchPage;
import com.sb24.asanzeman.integration.yaghut.YaghutClient;
import com.sb24.asanzeman.integration.yaghut.YaghutSessionManager;
import com.sb24.asanzeman.request.domain.model.BranchData;
import com.sb24.asanzeman.request.domain.port.out.BranchSourcePort;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Yaghut {@code branches} with the technical account (D35), paged with {@code offset}/{@code length} until
 * {@code totalRecord} is reached. A page that comes back empty before the total is reached would loop forever, so
 * it is treated as a broken answer.
 */
@Component
public class YaghutBranchSource implements BranchSourcePort {

    private static final String SERVICE = "Yaghut";

    private final YaghutClient client;
    private final YaghutSessionManager sessions;
    private final int pageSize;

    public YaghutBranchSource(YaghutClient client, YaghutSessionManager sessions,
            @Value("${azs.branch.sync.page-size:100}") int pageSize) {
        this.client = client;
        this.sessions = sessions;
        this.pageSize = pageSize;
    }

    @Override
    public List<BranchData> fetchAll() {
        List<BranchData> all = new ArrayList<>();
        long total;
        do {
            int offset = all.size();
            YaghutBranchPage page = sessions.withTechnicalSession(session -> client.branches(session, offset, pageSize));
            total = page.totalRecord();
            if (page.branches().isEmpty() && all.size() < total) {
                throw new TemporarilyUnavailableException(SERVICE, new IllegalStateException(
                        "branches page at offset " + offset + " is empty but totalRecord is " + total));
            }
            for (YaghutBranchPage.Branch b : page.branches()) {
                all.add(new BranchData(b.branchCode(), b.name(), b.city(), b.address(), b.tel(), b.fax(), b.zipCode()));
            }
        } while (all.size() < total);
        return all;
    }
}
