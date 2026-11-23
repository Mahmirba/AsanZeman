package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.model.RequestDetails;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.request.domain.port.in.CustomerRequestsUseCase;
import com.sb24.asanzeman.request.domain.port.out.RequestQueryPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** What a customer sees of their own requests. Reads only. */
@Service
public class CustomerRequestService implements CustomerRequestsUseCase {

    private final RequestQueryPort queries;

    public CustomerRequestService(RequestQueryPort queries) {
        this.queries = queries;
    }

    @Override
    public List<RequestSummary> list(String nationalCode) {
        return queries.forCustomer(nationalCode);
    }

    @Override
    public RequestDetails get(String nationalCode, UUID requestId) {
        return queries.detailsForCustomer(nationalCode, requestId).orElseThrow(RequestNotFoundException::new);
    }

}
