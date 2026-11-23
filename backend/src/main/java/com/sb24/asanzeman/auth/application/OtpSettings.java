package com.sb24.asanzeman.auth.application;

import java.time.Duration;

/**
 * The customer login's limits (D34, D7): a code is good for {@code validity}; {@code maxWrongAttempts} wrong codes lock
 * the customer for {@code lockout}; the session lasts {@code sessionTtl}.
 */
public record OtpSettings(Duration validity, int maxWrongAttempts, Duration lockout, Duration sessionTtl) {
}
