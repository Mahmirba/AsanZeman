package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.RequestSearchCriteria;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.shared.model.JalaliDate;
import com.sb24.asanzeman.shared.model.NationalCode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The tracking filters of SRS UC-AZS-13 as the panel sends them, turned into a search. Dates are Jalali
 * {@code yyyy/MM/dd}: "from" is the start of that day in Tehran, "to" includes the whole of that day.
 */
final class RequestFilters {

    private static final ZoneId TEHRAN = ZoneId.of("Asia/Tehran");

    private RequestFilters() {
    }

    static RequestSearchCriteria of(UUID agencyId, String nationalCode, String customerNumber, List<String> types,
            String trackingCode, String name, String registeredFrom, String registeredTo) {
        String code = blankToNull(nationalCode);
        if (code != null) {
            code = NationalCode.parse(code)
                    .orElseThrow(() -> new InvalidRequestDataException("the national code is not valid")).value();
        }
        return new RequestSearchCriteria(agencyId, code, blankToNull(customerNumber), types(types),
                blankToNull(trackingCode), blankToNull(name), startOf(registeredFrom, 0), startOf(registeredTo, 1));
    }

    private static Set<RequestType> types(List<String> types) {
        if (types == null) {
            return Set.of();
        }
        try {
            return types.stream().map(t -> RequestType.valueOf(t.trim().toUpperCase())).collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestDataException("a request type is ISSUANCE or REVOCATION");
        }
    }

    /** The start of the Jalali day plus {@code plusDays}, or {@code null} for no date. */
    private static Instant startOf(String jalali, int plusDays) {
        if (blankToNull(jalali) == null) {
            return null;
        }
        try {
            return JalaliDate.parse(jalali).toGregorian().plusDays(plusDays).atStartOfDay(TEHRAN).toInstant();
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestDataException("a date must be a real Jalali date, yyyy/MM/dd");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
