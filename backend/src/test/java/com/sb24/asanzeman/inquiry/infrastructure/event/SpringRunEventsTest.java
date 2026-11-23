package com.sb24.asanzeman.inquiry.infrastructure.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sb24.asanzeman.inquiry.domain.model.InquiryVerdict.Decision;
import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunFinished;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SpringRunEventsTest {

    @Test
    void theEndOfARunIsPublishedAsAnApplicationEvent() {
        var publisher = mock(ApplicationEventPublisher.class);
        var event = new InquiryRunFinished(UUID.randomUUID(), UUID.randomUUID(), Decision.PROCEED, null);

        new SpringRunEvents(publisher).publish(event);

        verify(publisher).publishEvent(event);
    }
}
