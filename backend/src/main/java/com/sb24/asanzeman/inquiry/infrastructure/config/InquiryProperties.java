package com.sb24.asanzeman.inquiry.infrastructure.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code azs.inquiry.*} (D3, D26, D27, 5.1): the stages and their deadlines, and the run lifecycle: a run still going
 * after {@code staleRunAfter} is dead, a blocked customer waits {@code customerRetryCooldown}, and a passed inquiry is
 * reused for {@code passedResultReuse}.
 */
@ConfigurationProperties("azs.inquiry")
public record InquiryProperties(String stages, List<Duration> stageDeadlines, Duration staleRunAfter,
        Duration customerRetryCooldown, Duration passedResultReuse) {

    public InquiryProperties {
        staleRunAfter = staleRunAfter == null ? Duration.ofSeconds(60) : staleRunAfter;
        customerRetryCooldown = customerRetryCooldown == null ? Duration.ofSeconds(60) : customerRetryCooldown;
        passedResultReuse = passedResultReuse == null ? Duration.ofHours(1) : passedResultReuse;
    }
}
