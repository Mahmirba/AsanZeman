package com.sb24.asanzeman.notification.domain.port.in;

/** Sends the SMS that are due (6.4). Called by the scheduler; one run at a time. */
public interface DispatchSmsUseCase {

    /** @return how many messages were tried in this run */
    int dispatchDue();
}
