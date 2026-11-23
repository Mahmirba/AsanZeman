package com.sb24.asanzeman.inquiry.application;

import java.time.Duration;

/**
 * The run lifecycle (5.1): a run still going after {@code staleAfter} is dead; a blocked customer waits
 * {@code retryCooldown} before trying again; a passed inquiry is reused for {@code reuseWindow}.
 */
public record InquiryRunSettings(Duration staleAfter, Duration retryCooldown, Duration reuseWindow) {
}
