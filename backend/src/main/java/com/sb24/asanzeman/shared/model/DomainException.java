package com.sb24.asanzeman.shared.model;

import java.util.List;

/**
 * Base class for business errors raised by domain and application code. Framework-free: it carries
 * a stable {@link #code()} (also the {@code messages_fa} key), the {@link Kind} that the web adapter
 * maps to an HTTP status, and message arguments. The Persian text is resolved in the web adapter.
 */
public abstract class DomainException extends RuntimeException {

    /** How the web adapter should report the error (D48). */
    public enum Kind {
        /** Input is malformed or violates a rule: 400. */
        BAD_REQUEST,
        /** The caller is not allowed to act on this object: 403. */
        FORBIDDEN,
        /** The object does not exist (or not for this caller): 404. */
        NOT_FOUND,
        /** The command is not valid in the object's current state, or it is locked by someone else: 409. */
        CONFLICT,
        /** A bank service is unavailable; the caller should try later: 503, never 400. */
        TEMPORARILY_UNAVAILABLE
    }

    private final String code;
    private final Kind kind;
    private final List<Object> args;

    protected DomainException(String code, Kind kind, Object... args) {
        super(code);
        this.code = code;
        this.kind = kind;
        this.args = List.of(args);
    }

    protected DomainException(String code, Kind kind, Throwable cause, Object... args) {
        super(code, cause);
        this.code = code;
        this.kind = kind;
        this.args = List.of(args);
    }

    /** Stable machine-readable code, e.g. {@code request.locked-by-other}; also the message key. */
    public String code() {
        return code;
    }

    public Kind kind() {
        return kind;
    }

    /** Arguments for the {@code {0}}, {@code {1}}… placeholders of the Persian message. */
    public List<Object> args() {
        return args;
    }
}
