package com.sb24.asanzeman.inquiry.domain.port.out;

import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunFinished;

/** Tells the rest of the system that a run ended. */
public interface RunEventsPort {

    void publish(InquiryRunFinished event);
}
