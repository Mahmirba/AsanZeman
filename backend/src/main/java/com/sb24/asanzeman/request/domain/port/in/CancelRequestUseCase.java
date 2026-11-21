package com.sb24.asanzeman.request.domain.port.in;

/** Cancels a request before «اقدام»; after it, a revocation is needed (4.2, D11). */
public interface CancelRequestUseCase {

    /**
     * @throws com.sb24.asanzeman.request.domain.model.RequestNotFoundException            unknown, or another agency's
     * @throws com.sb24.asanzeman.request.domain.model.RequestBusyException                 another user holds the row
     * @throws com.sb24.asanzeman.request.domain.model.RequestNotCancellableException       already finished
     * @throws com.sb24.asanzeman.request.domain.model.RevocationNotAvailableException      needs a revocation request
     */
    CancelRequestResult cancel(CancelRequestCommand command);
}
