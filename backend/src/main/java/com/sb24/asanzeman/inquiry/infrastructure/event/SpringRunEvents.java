package com.sb24.asanzeman.inquiry.infrastructure.event;

import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunFinished;
import com.sb24.asanzeman.inquiry.domain.port.out.RunEventsPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes the end of a run as a Spring application event, which the request module listens to. */
@Component
public class SpringRunEvents implements RunEventsPort {

    private final ApplicationEventPublisher publisher;

    public SpringRunEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void publish(InquiryRunFinished event) {
        publisher.publishEvent(event);
    }
}
