package com.sb24.asanzeman.auth.domain.port.out;

import com.sb24.asanzeman.auth.domain.model.YaghutAccount;
import java.util.Optional;

/** Where a customer's login code goes: the mobile the bank holds for them, read with their own Yaghut session. */
public interface CustomerMobilePort {

    /**
     * @return {@code 09xxxxxxxxx}; empty when Yaghut holds no mobile for the customer
     * @throws com.sb24.asanzeman.auth.domain.model.SuspectedCustomerException when the user is not a bank customer
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException    when Yaghut cannot answer
     */
    Optional<String> mobileOf(YaghutAccount account, String nationalCode);
}
