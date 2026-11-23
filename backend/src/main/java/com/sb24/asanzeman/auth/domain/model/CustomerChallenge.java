package com.sb24.asanzeman.auth.domain.model;

import java.util.UUID;

/** The answer to the first step of the customer login: which code to check, and for how long it is good. */
public record CustomerChallenge(UUID challengeId, long expiresInSeconds) {
}
