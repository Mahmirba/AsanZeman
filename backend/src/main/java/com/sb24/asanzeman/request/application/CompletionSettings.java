package com.sb24.asanzeman.request.application;

import java.time.Duration;

/** The time the customer has to complete a request after the agency registered it (default 7 days): later it is rejected. */
public record CompletionSettings(Duration window) {
}
