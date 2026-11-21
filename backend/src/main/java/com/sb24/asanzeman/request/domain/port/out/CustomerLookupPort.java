package com.sb24.asanzeman.request.domain.port.out;

import com.sb24.asanzeman.request.domain.model.CustomerSnapshot;
import java.util.UUID;

/** Yaghut {@code customerDetailInfo} with the agency's own session (D21, D35). */
public interface CustomerLookupPort {

    /**
     * @throws com.sb24.asanzeman.request.domain.model.NotBankCustomerException         Yaghut does not know the code
     * @throws com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException       the agency must log in again
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException          any technical failure
     */
    CustomerSnapshot find(UUID sessionId, String nationalCode);
}
