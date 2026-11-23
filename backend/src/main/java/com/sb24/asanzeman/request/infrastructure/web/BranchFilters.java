package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.BranchSearchCriteria;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.shared.model.NationalCode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The branch's search filters as the panel sends them, turned into a search. Dates are Jalali {@code yyyy/MM/dd}: "from"
 * is the start of that day in Tehran, "to" includes the whole of that day. The branch is added later, from the user.
 */
final class BranchFilters {

    private BranchFilters() {
    }

    static BranchSearchCriteria of(List<String> statuses, String nationalCode, String customerNumber,
            String trackingCode, String registeredFrom, String registeredTo) {
        String code = RequestFilters.blankToNull(nationalCode);
        if (code != null) {
            code = NationalCode.parse(code)
                    .orElseThrow(() -> new InvalidRequestDataException("the national code is not valid")).value();
        }
        return new BranchSearchCriteria(null, statuses(statuses), code, RequestFilters.blankToNull(customerNumber),
                RequestFilters.blankToNull(trackingCode), RequestFilters.startOf(registeredFrom, 0),
                RequestFilters.startOf(registeredTo, 1));
    }

    private static Set<RequestStatus> statuses(List<String> statuses) {
        if (statuses == null) {
            return Set.of();
        }
        try {
            return statuses.stream().map(s -> RequestStatus.valueOf(s.trim().toUpperCase()))
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestDataException("a status is one of the request statuses");
        }
    }
}
