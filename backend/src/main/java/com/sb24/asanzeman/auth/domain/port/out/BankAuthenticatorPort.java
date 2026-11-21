package com.sb24.asanzeman.auth.domain.port.out;

import com.sb24.asanzeman.auth.domain.model.BankAuthentication;

/** The bank's authentication and authorization service (AAA): checks the password and says which roles apply. */
public interface BankAuthenticatorPort {

    /**
     * @param userName the user name exactly as the user typed it
     * @return the outcome; refused credentials are a result, not an exception
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException on a technical failure
     */
    BankAuthentication authenticate(String userName, String password);
}
