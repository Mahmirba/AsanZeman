package com.sb24.asanzeman.request.infrastructure.external;

import com.sb24.asanzeman.request.domain.port.out.RegistrationNotifierPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * <b>Temporary.</b> The SMS outbox is the next step; until it exists this only logs, so a registration works end to
 * end without sending the customer anything. It is replaced by an outbox adapter, not kept next to it.
 */
@Component
public class LogOnlyRegistrationNotifier implements RegistrationNotifierPort {

    private static final Logger log = LoggerFactory.getLogger(LogOnlyRegistrationNotifier.class);

    @Override
    public void customerRegistered(String mobile, String agencyName, String trackingCode) {
        log.warn("SMS NOT SENT (outbox not built yet): request {} registered by {} for mobile {}", trackingCode,
                agencyName, mobile);
    }
}
