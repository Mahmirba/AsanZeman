package com.sb24.asanzeman.integration.yaghut;

/**
 * What {@code loginStatic} gave back that Asan Zeman uses (5.4). The login checks (temporary session, expired
 * password, agency {@code cif}, customer national code) are done by the auth module, not here.
 *
 * @param session                the {@code JSESSIONID} and its end time
 * @param name                   the user's name
 * @param cif                    identifies an <b>agency</b>; {@code null} otherwise
 * @param code                   the national code (customers)
 * @param temporarySessionId     set when Yaghut issued only a temporary session
 * @param temporaryCause         {@code TICKET_LOGIN}, {@code DIGITAL_SIGNATURE_LOGIN} or {@code OTHER}
 * @param passwordExpirationJson the raw {@code passwordExpirationBean} as JSON text, or {@code null} when absent
 */
public record YaghutLogin(
        YaghutSession session,
        String name,
        String cif,
        String code,
        String temporarySessionId,
        String temporaryCause,
        String passwordExpirationJson) {
}
