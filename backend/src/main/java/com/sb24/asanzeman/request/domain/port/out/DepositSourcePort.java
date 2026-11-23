package com.sb24.asanzeman.request.domain.port.out;

import com.sb24.asanzeman.request.domain.model.CustomerDeposit;
import java.util.List;
import java.util.UUID;

/** The customer's deposits at the bank, read with the customer's own Yaghut session (D35). */
public interface DepositSourcePort {

    /**
     * @param sessionId the customer's Asan Zeman session; their Yaghut session is found through it
     * @param cif       the customer number saved with the request
     * @throws com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException the Yaghut session has ended
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException    Yaghut cannot answer
     */
    List<CustomerDeposit> deposits(UUID sessionId, String cif);
}
