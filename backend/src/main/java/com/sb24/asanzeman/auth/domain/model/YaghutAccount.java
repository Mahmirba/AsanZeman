package com.sb24.asanzeman.auth.domain.model;

import java.time.Instant;

/**
 * What Yaghut's {@code loginStatic} said about a user who logged in (5.4): who they are and the session Yaghut opened
 * for them. Used for agencies (identified by {@code cif}) and customers (identified by {@code code}).
 *
 * @param name              the user's name
 * @param cif               identifies an agency; {@code null} otherwise
 * @param code              the national code (customers)
 * @param sessionId         the {@code JSESSIONID}; a secret, never shown to a panel or printed
 * @param sessionExpiresAt  when Yaghut ends the session; {@code null} when Yaghut gave no end time
 * @param temporarySession  Yaghut issued only a temporary session (ticket, digital signature or other)
 * @param passwordExpired   the user's password has expired
 */
public record YaghutAccount(String name, String cif, String code, String sessionId, Instant sessionExpiresAt,
        boolean temporarySession, boolean passwordExpired) {

    public YaghutAccount {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("a Yaghut account needs a session id");
        }
    }

    @Override
    public String toString() {
        return "YaghutAccount[name=" + name + ", cif=" + cif + ", code=" + code + ", sessionExpiresAt="
                + sessionExpiresAt + "]";
    }
}
