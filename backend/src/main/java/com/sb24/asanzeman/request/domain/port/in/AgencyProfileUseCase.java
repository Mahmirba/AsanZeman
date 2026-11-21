package com.sb24.asanzeman.request.domain.port.in;

import java.util.UUID;

public interface AgencyProfileUseCase {

    /** @throws com.sb24.asanzeman.request.domain.model.AgencyNotActiveException the agency is unknown or inactive */
    AgencyProfile profile(UUID agencyId);
}
