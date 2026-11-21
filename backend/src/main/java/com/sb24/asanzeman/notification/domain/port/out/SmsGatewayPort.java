package com.sb24.asanzeman.notification.domain.port.out;

/** The SMS centre: Notifier {@code SendSimpleSms} (6.4, D40). */
public interface SmsGatewayPort {

    /**
     * @param gatewayNumber one recipient, {@code 0989xxxxxxxxx}
     * @return the SMS centre's {@code smsId}
     * @throws com.sb24.asanzeman.notification.domain.model.SmsRejectedException     400, never retried
     * @throws com.sb24.asanzeman.notification.domain.model.SmsCredentialsException  403, long backoff
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException       anything else, retried
     */
    String send(String gatewayNumber, String text);
}
