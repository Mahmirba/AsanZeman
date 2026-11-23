package com.sb24.asanzeman.inquiry.domain.model;

/** Where a run is: asking the services, done, or given up on because it never finished (5.1, a restart killed it). */
public enum RunStatus {
    RUNNING,
    FINISHED,
    STALE
}
