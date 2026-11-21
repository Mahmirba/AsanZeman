package com.sb24.asanzeman.notification.infrastructure.external;

import com.sb24.asanzeman.integration.notifier.NotifierClient;
import com.sb24.asanzeman.integration.notifier.NotifierCredentialsException;
import com.sb24.asanzeman.integration.notifier.NotifierRejectedException;
import com.sb24.asanzeman.notification.domain.model.SmsCredentialsException;
import com.sb24.asanzeman.notification.domain.model.SmsRejectedException;
import com.sb24.asanzeman.notification.domain.port.out.SmsGatewayPort;
import org.springframework.stereotype.Component;

/** {@code SendSimpleSms} through the Notifier client (6.4, D40); its 400 and 403 become the domain outcomes. */
@Component
public class NotifierSmsGateway implements SmsGatewayPort {

    private final NotifierClient client;

    public NotifierSmsGateway(NotifierClient client) {
        this.client = client;
    }

    @Override
    public String send(String gatewayNumber, String text) {
        try {
            return client.sendSimpleSms(gatewayNumber, text);
        } catch (NotifierRejectedException e) {
            throw new SmsRejectedException(e.getMessage());
        } catch (NotifierCredentialsException e) {
            throw new SmsCredentialsException(e.getMessage());
        }
    }
}
