package com.sb24.asanzeman.auth.application;

import java.time.Duration;

/** Token lifetimes (D5): access 15 minutes, refresh (the whole session) 8 hours for bank users. Configurable. */
public record TokenSettings(Duration accessTtl, Duration refreshTtl) {
}
