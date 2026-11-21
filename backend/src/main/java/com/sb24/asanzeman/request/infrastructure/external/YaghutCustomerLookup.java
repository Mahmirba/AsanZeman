package com.sb24.asanzeman.request.infrastructure.external;

import com.sb24.asanzeman.auth.domain.model.YaghutAccessDeniedException;
import com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException;
import com.sb24.asanzeman.auth.domain.model.YaghutUserSession;
import com.sb24.asanzeman.auth.domain.port.in.YaghutUserSessionUseCase;
import com.sb24.asanzeman.integration.yaghut.YaghutClient;
import com.sb24.asanzeman.integration.yaghut.YaghutCustomer;
import com.sb24.asanzeman.integration.yaghut.YaghutException;
import com.sb24.asanzeman.integration.yaghut.YaghutSession;
import com.sb24.asanzeman.request.domain.model.CustomerSnapshot;
import com.sb24.asanzeman.request.domain.model.NotBankCustomerException;
import com.sb24.asanzeman.request.domain.port.out.CustomerLookupPort;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Yaghut {@code customerDetailInfo} with the <b>agency's own</b> Yaghut session (D21, D35), found through the
 * agency's Asan Zeman session. When Yaghut says that session is invalid, the Asan Zeman session is ended too, and the
 * agency must log in again: its password is not stored, so nothing can renew the session.
 */
@Component
public class YaghutCustomerLookup implements CustomerLookupPort {

    private final YaghutUserSessionUseCase userSessions;
    private final YaghutClient client;

    public YaghutCustomerLookup(YaghutUserSessionUseCase userSessions, YaghutClient client) {
        this.userSessions = userSessions;
        this.client = client;
    }

    @Override
    public CustomerSnapshot find(UUID sessionId, String nationalCode) {
        YaghutUserSession session = userSessions.require(sessionId);
        YaghutCustomer c;
        try {
            c = client.customerDetailInfo(new YaghutSession(session.jsessionId(), session.expiresAt()), nationalCode);
        } catch (YaghutException e) {
            switch (e.fault()) {
                case INVALID_CUSTOMER:
                    throw new NotBankCustomerException();
                case INVALID_SESSION:
                    userSessions.end(sessionId);
                    throw new YaghutSessionExpiredException();
                case SERVICE_ACCESS_DENIED:
                    throw new YaghutAccessDeniedException();
                default:
                    throw new TemporarilyUnavailableException("Yaghut", e);
            }
        }
        return new CustomerSnapshot(c.cif(), c.code() == null ? nationalCode : c.code(), c.firstName(),
                c.lastName(), c.fullName(), c.fatherName(), c.birthDate(), c.mobile(), c.ssn(),
                c.certificateSerial(), c.certificateSeries(), c.personalityType(), c.gender());
    }
}
