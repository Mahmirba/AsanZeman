package com.sb24.asanzeman.auth.domain.model;

/** Where a call came from, for the audit log (6): the caller's address and user agent. Either may be unknown. */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo unknown() {
        return new ClientInfo(null, null);
    }
}
