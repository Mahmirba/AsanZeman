package com.sb24.asanzeman.notification.domain.model;

/** The SMS centre can never accept this message (400): it goes to the dead-letter list with an alert (6.4). */
public class SmsRejectedException extends RuntimeException {

    public SmsRejectedException(String message) {
        super(message);
    }
}
