package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.model.TrackingCode;
import com.sb24.asanzeman.request.domain.port.out.TrackingCounterPort;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes tracking codes (D12). The day is the Jalali day in Tehran, so a request registered at 00:30 Tehran time
 * belongs to the new day. It joins the registration's transaction: the counter row lock is held until commit, and a
 * failed registration does not use up a number.
 */
@Component
public class TrackingCodeGenerator {


    private final TrackingCounterPort counters;
    private final Clock clock;

    public TrackingCodeGenerator(TrackingCounterPort counters, Clock clock) {
        this.counters = counters;
        this.clock = clock;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public TrackingCode next(String branchCode, String agencyCode) {
        JalaliDate day = JalaliDate.ofInstant(clock.instant());
        int counter = counters.next(branchCode, agencyCode, day.toYyMmDd());
        return TrackingCode.of(branchCode, agencyCode, day, counter);
    }
}
