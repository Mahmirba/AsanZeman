package com.sb24.asanzeman.auth.domain.port.in;

import com.sb24.asanzeman.auth.domain.model.ClientInfo;
import com.sb24.asanzeman.auth.domain.model.IssuedTokens;
import com.sb24.asanzeman.auth.domain.model.Session;

/** Starts a session for an already authenticated subject: issues the tokens and audits the login. */
public interface StartSessionUseCase {

    IssuedTokens start(Session session, ClientInfo client);
}
