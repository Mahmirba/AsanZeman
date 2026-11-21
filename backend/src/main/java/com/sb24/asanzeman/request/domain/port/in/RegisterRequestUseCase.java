package com.sb24.asanzeman.request.domain.port.in;

/** Registers a request for the logged-in agency (SRS UC-AZS-8). */
public interface RegisterRequestUseCase {

    /**
     * Order: national code, branch, the customer from Yaghut (the agency's own session, outside any transaction),
     * then in one transaction the agency lock, the duplicate guard (D47), the tracking code, the request and the
     * customer's SMS.
     *
     * @throws com.sb24.asanzeman.request.domain.model.InvalidRequestDataException     400
     * @throws com.sb24.asanzeman.request.domain.model.BranchNotAvailableException     400
     * @throws com.sb24.asanzeman.request.domain.model.NotBankCustomerException        400
     * @throws com.sb24.asanzeman.request.domain.model.CustomerUnderageException       400
     * @throws com.sb24.asanzeman.request.domain.model.CustomerNotActualException      400
     * @throws com.sb24.asanzeman.request.domain.model.CustomerMobileMissingException  400
     * @throws com.sb24.asanzeman.request.domain.model.DuplicateRegistrationException  409
     * @throws com.sb24.asanzeman.request.domain.model.AgencyNotActiveException        403
     * @throws com.sb24.asanzeman.auth.domain.model.YaghutSessionExpiredException      401, log in again
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException         503
     */
    RegisteredRequest register(RegisterRequestCommand command);
}
