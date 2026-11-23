package com.sb24.asanzeman.auth.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code azs.otp.*} (D34): how long a code is good, how many wrong ones are allowed, how long the lockout lasts. */
@ConfigurationProperties("azs.otp")
public record OtpProperties(Duration validity, Integer maxWrongAttempts, Duration lockout) {

    public OtpProperties {
        validity = validity == null ? Duration.ofSeconds(120) : validity;
        maxWrongAttempts = maxWrongAttempts == null ? 3 : maxWrongAttempts;
        lockout = lockout == null ? Duration.ofMinutes(15) : lockout;
    }
}
