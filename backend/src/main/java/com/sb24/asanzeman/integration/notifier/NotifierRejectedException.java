package com.sb24.asanzeman.integration.notifier;

/** The Notifier answered 400: the number or the request is malformed, so sending again can never succeed (6.4). */
public class NotifierRejectedException extends RuntimeException {

    public NotifierRejectedException(String message) {
        super(message);
    }
}
