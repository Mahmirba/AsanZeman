package com.sb24.asanzeman.request.domain.port.out;

import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.RequestDetails;
import com.sb24.asanzeman.request.domain.model.RequestSearchCriteria;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read side of the agency panel: tracking list and details, always for one agency. Newest request first. */
public interface RequestQueryPort {

    /** @param page starting at 0 */
    PageResult<RequestSummary> search(RequestSearchCriteria criteria, int page, int size);

    /** @return the request only when it belongs to {@code agencyId} */
    Optional<RequestDetails> details(UUID agencyId, UUID requestId);

    /** Every request made for this customer, newest first. */
    List<RequestSummary> forCustomer(String nationalCode);

    /** @return the request only when it was made for the customer with this national code */
    Optional<RequestDetails> detailsForCustomer(String nationalCode, UUID requestId);
}
