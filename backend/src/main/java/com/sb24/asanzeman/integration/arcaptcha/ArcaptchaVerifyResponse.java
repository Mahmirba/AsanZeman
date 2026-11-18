package com.sb24.asanzeman.integration.arcaptcha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** ARcaptcha's JSON answer: {@code { "success": true, "error-codes": [...] }}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArcaptchaVerifyResponse(boolean success, @JsonProperty("error-codes") List<String> errorCodes) {

    public ArcaptchaVerifyResponse {
        errorCodes = errorCodes == null ? List.of() : List.copyOf(errorCodes);
    }
}
