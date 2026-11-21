package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.RequestDetails;
import com.sb24.asanzeman.shared.model.JalaliDate;
import java.util.List;
import org.springframework.context.MessageSource;

/** The request with its companions (SRS UC-AZS-13, alternative flow 2). Collateral details arrive with Phase 3. */
public record RequestDetailsResponse(RequestSummaryResponse request, List<CompanionResponse> companions) {

    /** A companion; the birth date is Jalali {@code yyyy/MM/dd}. */
    public record CompanionResponse(String fullName, String nationalCode, String birthDate, String passportNumber) {
    }

    static RequestDetailsResponse from(RequestDetails details, MessageSource messages) {
        return new RequestDetailsResponse(RequestSummaryResponse.from(details.summary(), messages),
                details.companions().stream().map(c -> new CompanionResponse(c.fullName(), c.nationalCode(),
                        JalaliDate.fromGregorian(c.birthDate()).toSlashed(), c.passportNumber())).toList());
    }
}
