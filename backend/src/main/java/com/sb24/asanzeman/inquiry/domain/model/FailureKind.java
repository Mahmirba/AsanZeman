package com.sb24.asanzeman.inquiry.domain.model;

/** Why an inquiry has no usable answer (5.1). {@code CANCELLED}: the run was stopped by a rejection. */
public enum FailureKind {
    TIMEOUT,
    UNAVAILABLE,
    CIRCUIT_OPEN,
    BAD_RESPONSE,
    CANCELLED
}
