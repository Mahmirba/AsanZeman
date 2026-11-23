package com.sb24.asanzeman.integration.inquiry;

/** An inquiry service could not be reached or did not answer in time, after the retries (5.1, D4). */
public class InquiryCallException extends RuntimeException {

    public enum Kind {
        TIMEOUT,
        UNAVAILABLE
    }

    private final Kind kind;

    public InquiryCallException(Kind kind, Throwable cause) {
        super("inquiry service " + kind, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
