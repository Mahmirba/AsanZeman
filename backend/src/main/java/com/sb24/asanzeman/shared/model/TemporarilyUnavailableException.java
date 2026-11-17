package com.sb24.asanzeman.shared.model;

/** A bank service failed technically (timeout, 5xx, circuit open). Reported as 503 "try later". */
public class TemporarilyUnavailableException extends DomainException {

    public static final String CODE = "service.temporarily-unavailable";

    public TemporarilyUnavailableException(String service, Throwable cause) {
        super(CODE, Kind.TEMPORARILY_UNAVAILABLE, cause, service);
    }

    public String service() {
        return (String) args().get(0);
    }
}
