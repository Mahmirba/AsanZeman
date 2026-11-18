package com.sb24.asanzeman.auth.domain.model;

/** What an ARcaptcha answer means for the login (6.5). */
public enum CaptchaVerdict {
    /** The token is valid: the login continues. */
    SOLVED,
    /** The token is wrong, missing, expired or already used: the user solves the captcha again (400). */
    WRONG_TOKEN,
    /** Our keys or request are wrong: an operations problem, shown to the user as "try again later". */
    MISCONFIGURED
}
