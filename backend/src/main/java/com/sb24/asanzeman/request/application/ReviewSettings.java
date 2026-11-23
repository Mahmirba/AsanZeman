package com.sb24.asanzeman.request.application;

import java.time.Duration;

/** The branch review's limits (4.1): a review nobody has touched for {@code idleTimeout} goes back to the queue. */
public record ReviewSettings(Duration idleTimeout) {
}
