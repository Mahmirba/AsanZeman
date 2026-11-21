package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.RequestDetails;
import java.util.UUID;

public interface GetRequestUseCase {

    /** @throws com.sb24.asanzeman.request.domain.model.RequestNotFoundException unknown, or another agency's */
    RequestDetails get(UUID agencyId, UUID requestId);
}
