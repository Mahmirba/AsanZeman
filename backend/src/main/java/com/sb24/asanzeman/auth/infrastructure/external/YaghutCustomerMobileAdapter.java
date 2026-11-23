package com.sb24.asanzeman.auth.infrastructure.external;

import com.sb24.asanzeman.auth.domain.model.SuspectedCustomerException;
import com.sb24.asanzeman.auth.domain.model.YaghutAccessDeniedException;
import com.sb24.asanzeman.auth.domain.model.YaghutAccount;
import com.sb24.asanzeman.auth.domain.port.out.CustomerMobilePort;
import com.sb24.asanzeman.integration.yaghut.YaghutClient;
import com.sb24.asanzeman.integration.yaghut.YaghutException;
import com.sb24.asanzeman.integration.yaghut.YaghutSession;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Yaghut {@code customerDetailInfo} with the customer's own session, just opened by their login (D35). */
@Component
public class YaghutCustomerMobileAdapter implements CustomerMobilePort {

    private final YaghutClient client;

    public YaghutCustomerMobileAdapter(YaghutClient client) {
        this.client = client;
    }

    @Override
    public Optional<String> mobileOf(YaghutAccount account, String nationalCode) {
        try {
            String mobile = client.customerDetailInfo(new YaghutSession(account.sessionId(),
                    account.sessionExpiresAt()), nationalCode).mobile();
            return Optional.ofNullable(mobile).map(String::trim).filter(m -> m.matches("09\\d{9}"));
        } catch (YaghutException e) {
            throw switch (e.fault()) {
                case INVALID_CUSTOMER -> new SuspectedCustomerException();
                case SERVICE_ACCESS_DENIED -> new YaghutAccessDeniedException();
                default -> new TemporarilyUnavailableException("Yaghut", e);
            };
        }
    }
}
