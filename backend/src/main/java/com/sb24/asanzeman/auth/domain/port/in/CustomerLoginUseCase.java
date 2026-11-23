package com.sb24.asanzeman.auth.domain.port.in;

import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.CustomerChallenge;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;

/**
 * Customer login in two steps (6.3): ARcaptcha, Yaghut {@code loginStatic} and an SMS code, then the code's check and a
 * 30-minute session without a refresh token (D7).
 */
public interface CustomerLoginUseCase {

    /**
     * Step 1: sends the SMS code to the mobile the bank holds for the customer.
     *
     * @throws com.sb24.asanzeman.auth.domain.model.CaptchaFailedException                400
     * @throws com.sb24.asanzeman.auth.domain.model.InvalidYaghutCredentialsException     401
     * @throws com.sb24.asanzeman.auth.domain.model.SuspectedCustomerException            403, not a bank customer
     * @throws com.sb24.asanzeman.auth.domain.model.CustomerMobileMissingException        403
     * @throws com.sb24.asanzeman.auth.domain.model.OtpLockedException                    403
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException            503
     */
    CustomerChallenge login(String username, String password, String captchaToken);

    /**
     * Step 2: checks the code and starts the session. A code works once.
     *
     * @throws com.sb24.asanzeman.auth.domain.model.OtpExpiredException  400, too old, used, or unknown
     * @throws com.sb24.asanzeman.auth.domain.model.OtpWrongException    400
     * @throws com.sb24.asanzeman.auth.domain.model.OtpLockedException   403
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException 503
     */
    IssuedTokens verifyOtp(java.util.UUID challengeId, String code, ClientInfo client);
}
