package com.sb24.asanzeman.integration.arcaptcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArcaptchaVerifyResponseTest {

    @Test
    void nullCodesBecomeAnEmptyList() {
        assertThat(new ArcaptchaVerifyResponse(true, null).errorCodes()).isEmpty();
    }

    @Test
    void keepsTheCodes() {
        assertThat(new ArcaptchaVerifyResponse(false, List.of("a", "b")).errorCodes()).containsExactly("a", "b");
    }
}
