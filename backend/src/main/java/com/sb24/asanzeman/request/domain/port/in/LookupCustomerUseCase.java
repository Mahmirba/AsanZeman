package com.sb24.asanzeman.request.domain.port.in;

import java.util.UUID;

/** Step «جستجو» of the registration form: the customer from Yaghut, checked with the same rules as registration. */
public interface LookupCustomerUseCase {

    /**
     * @param sessionId the agency's Asan Zeman session ({@code sid}), which finds its own Yaghut session
     * @throws com.sb24.asanzeman.request.domain.model.InvalidRequestDataException   the national code is not valid
     * @throws com.sb24.asanzeman.request.domain.model.NotBankCustomerException      Yaghut does not know the code
     * @throws com.sb24.asanzeman.request.domain.model.CustomerUnderageException     under 18
     * @throws com.sb24.asanzeman.request.domain.model.CustomerNotActualException    not a natural person
     * @throws com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException    log in again
     */
    CustomerLookupResult lookup(UUID sessionId, String nationalCode);
}
