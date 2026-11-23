package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.inquiry.domain.model.InquiryContext;
import com.sb24.asanzeman.inquiry.domain.model.InquiryRunView;
import com.sb24.asanzeman.inquiry.domain.port.in.InquiryRunUseCase;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestNotInquirableException;
import com.sb24.asanzeman.request.domain.port.in.CustomerInquiryUseCase;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Hands the customer's request to the inquiry module (5.1). The request is only ever read: it is the customer's own
 * (their national code is the token's), and the inquiries run from the data saved with it (D21). The customer's
 * mobile and birth date from the snapshot are what Shahkar and Sabte Ahval check against.
 */
@Service
public class CustomerInquiryService implements CustomerInquiryUseCase {

    private final RequestRepositoryPort requests;
    private final InquiryRunUseCase inquiries;

    public CustomerInquiryService(RequestRepositoryPort requests, InquiryRunUseCase inquiries) {
        this.requests = requests;
        this.inquiries = inquiries;
    }

    @Override
    public InquiryRunView start(String nationalCode, UUID requestId) {
        Request request = owned(nationalCode, requestId);
        if (!request.canBeInquired()) {
            throw new RequestNotInquirableException();
        }
        return inquiries.start(request.id(), new InquiryContext(request.customer().code(),
                request.customer().mobile(), request.amount().value(), request.branchCode(),
                request.customer().birthDate()));
    }

    @Override
    public InquiryRunView get(String nationalCode, UUID requestId, UUID runId) {
        return inquiries.get(owned(nationalCode, requestId).id(), runId);
    }

    private Request owned(String nationalCode, UUID requestId) {
        return requests.findById(requestId).filter(r -> r.customer().code().equals(nationalCode))
                .orElseThrow(RequestNotFoundException::new);
    }
}
