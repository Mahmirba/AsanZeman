package com.sb24.asanzeman.request.application;

import java.time.Duration;

/** {@code azs.request.duplicate-window}: how long a repeated registration for the same customer is refused (D47). */
public record RegistrationSettings(Duration duplicateWindow) {
}
