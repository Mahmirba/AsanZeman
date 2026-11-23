package com.sb24.asanzeman.auth.domain.port.in;

/** Housekeeping for the login tables: old one-time-code records and blacklist entries of tokens that have expired. */
public interface PurgeAuthDataUseCase {

    /** @return how many rows were deleted */
    int purge();
}
