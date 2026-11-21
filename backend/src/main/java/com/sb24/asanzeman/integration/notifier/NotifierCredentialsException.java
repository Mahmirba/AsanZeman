package com.sb24.asanzeman.integration.notifier;

/**
 * The Notifier answered 403 «please contact the SamanBank Sms Center support»: Asan Zeman's credentials or client
 * name are not active (6.4). A configuration problem: ops is alerted and the message is kept for a later try.
 */
public class NotifierCredentialsException extends RuntimeException {

    public NotifierCredentialsException(String message) {
        super(message);
    }
}
