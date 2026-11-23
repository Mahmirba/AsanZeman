package com.sb24.asanzeman.auth.infrastructure.external;

import com.sb24.asanzeman.auth.domain.port.out.OtpGatewayPort;
import com.sb24.asanzeman.integration.notifier.NotifierClient;
import com.sb24.asanzeman.integration.notifier.NotifierCredentialsException;
import com.sb24.asanzeman.integration.notifier.NotifierRejectedException;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import org.springframework.stereotype.Component;

/**
 * {@code SendOtp} and {@code OtpValidate} (D34). A refused request or refused credentials are our configuration's fault,
 * not the customer's: the client logs them as errors, and the customer is told to try later, never "wrong code".
 */
@Component
public class NotifierOtpGateway implements OtpGatewayPort {

    private final NotifierClient client;

    public NotifierOtpGateway(NotifierClient client) {
        this.client = client;
    }

    @Override
    public String send(String mobile) {
        try {
            return client.sendOtp(mobile);
        } catch (NotifierRejectedException | NotifierCredentialsException e) {
            throw new TemporarilyUnavailableException("Notifier", e);
        }
    }

    @Override
    public boolean validate(String mobile, String otpId, String code) {
        try {
            return client.validateOtp(mobile, otpId, code);
        } catch (NotifierRejectedException | NotifierCredentialsException e) {
            throw new TemporarilyUnavailableException("Notifier", e);
        }
    }
}
