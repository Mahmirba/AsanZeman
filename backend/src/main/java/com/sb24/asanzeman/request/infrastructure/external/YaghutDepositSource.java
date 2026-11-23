package com.sb24.asanzeman.request.infrastructure.external;

import com.sb24.asanzeman.auth.domain.model.YaghutAccessDeniedException;
import com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException;
import com.sb24.asanzeman.auth.domain.model.YaghutUserSession;
import com.sb24.asanzeman.auth.domain.port.in.YaghutUserSessionUseCase;
import com.sb24.asanzeman.integration.yaghut.YaghutClient;
import com.sb24.asanzeman.integration.yaghut.YaghutDeposit;
import com.sb24.asanzeman.integration.yaghut.YaghutException;
import com.sb24.asanzeman.integration.yaghut.YaghutSession;
import com.sb24.asanzeman.request.domain.model.CustomerDeposit;
import com.sb24.asanzeman.request.domain.port.out.DepositSourcePort;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Yaghut {@code deposit} with the <b>customer's own</b> Yaghut session (D35), found through their Asan Zeman session.
 * When Yaghut says that session is invalid, the Asan Zeman session is ended too and the customer logs in again.
 */
@Component
public class YaghutDepositSource implements DepositSourcePort {

    private final YaghutUserSessionUseCase userSessions;
    private final YaghutClient client;

    public YaghutDepositSource(YaghutUserSessionUseCase userSessions, YaghutClient client) {
        this.userSessions = userSessions;
        this.client = client;
    }

    @Override
    public List<CustomerDeposit> deposits(UUID sessionId, String cif) {
        YaghutUserSession session = userSessions.require(sessionId);
        List<YaghutDeposit> deposits;
        try {
            deposits = client.deposits(new YaghutSession(session.jsessionId(), session.expiresAt()), cif);
        } catch (YaghutException e) {
            switch (e.fault()) {
                case INVALID_SESSION:
                    userSessions.end(sessionId);
                    throw new YaghutSessionExpiredException();
                case SERVICE_ACCESS_DENIED:
                    throw new YaghutAccessDeniedException();
                default:
                    throw new TemporarilyUnavailableException("Yaghut", e);
            }
        }
        return deposits.stream().map(d -> new CustomerDeposit(d.number(), d.group(), d.typeCode(), d.owner(),
                d.signature(), d.status(), d.currency(), d.personality(), d.availableBalance())).toList();
    }
}
