package com.sb24.asanzeman.notification.domain.model;

/** The SMS centre refuses our credentials (403): ops is alerted, and the message waits with a long backoff (6.4). */
public class SmsCredentialsException extends RuntimeException {

    public SmsCredentialsException(String message) {
        super(message);
    }
}
