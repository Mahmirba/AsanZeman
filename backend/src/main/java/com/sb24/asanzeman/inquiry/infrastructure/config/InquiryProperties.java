package com.sb24.asanzeman.inquiry.infrastructure.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code azs.inquiry.*} (D26, D27): the stages and their deadlines. */
@ConfigurationProperties("azs.inquiry")
public record InquiryProperties(String stages, List<Duration> stageDeadlines) {
}
