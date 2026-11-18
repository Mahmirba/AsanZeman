package com.sb24.asanzeman.auth.domain.model;

import java.util.List;
import java.util.Set;

/**
 * ARcaptcha's answer: {@code { "success": true|false, "error-codes": [...] }} (6.5).
 *
 * @param success    whether the token was valid
 * @param errorCodes ARcaptcha's error codes, never {@code null}
 */
public record CaptchaResponse(boolean success, List<String> errorCodes) {

    /** Error codes that mean our own configuration or request is wrong, not the user's token. */
    static final Set<String> CONFIGURATION_ERRORS = Set.of(
            "missing-input-sitekey", "missing-input-secret", "invalid-input-sitekey", "invalid-input-secret",
            "bad-request");

    public CaptchaResponse {
        errorCodes = errorCodes == null ? List.of() : List.copyOf(errorCodes);
    }

    /** An answer that accepts the token without asking ARcaptcha (verification switched off). */
    public static CaptchaResponse accepted() {
        return new CaptchaResponse(true, List.of());
    }

    /**
     * {@code timeout-or-duplicate}, {@code invalid-input-response}, {@code missing-input-response} and a failure
     * with no known code all mean the user must solve the captcha again. A configuration code wins when both
     * kinds appear, because retrying cannot help the user.
     */
    public CaptchaVerdict verdict() {
        if (success) {
            return CaptchaVerdict.SOLVED;
        }
        for (String code : errorCodes) {
            if (CONFIGURATION_ERRORS.contains(code)) {
                return CaptchaVerdict.MISCONFIGURED;
            }
        }
        return CaptchaVerdict.WRONG_TOKEN;
    }
}
