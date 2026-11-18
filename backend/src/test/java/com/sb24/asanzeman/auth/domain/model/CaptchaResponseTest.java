package com.sb24.asanzeman.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CaptchaResponseTest {

    @Test
    void successIsSolved() {
        assertThat(new CaptchaResponse(true, List.of()).verdict()).isEqualTo(CaptchaVerdict.SOLVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"timeout-or-duplicate", "invalid-input-response", "missing-input-response", "unknown"})
    void userSideErrorsMeanSolveAgain(String code) {
        assertThat(new CaptchaResponse(false, List.of(code)).verdict()).isEqualTo(CaptchaVerdict.WRONG_TOKEN);
    }

    @Test
    void failureWithoutAnyCodeMeansSolveAgain() {
        assertThat(new CaptchaResponse(false, List.of()).verdict()).isEqualTo(CaptchaVerdict.WRONG_TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-input-sitekey", "missing-input-secret", "invalid-input-sitekey",
            "invalid-input-secret", "bad-request"})
    void configurationErrorsAreOurProblem(String code) {
        assertThat(new CaptchaResponse(false, List.of(code)).verdict()).isEqualTo(CaptchaVerdict.MISCONFIGURED);
    }

    @Test
    void configurationErrorWinsOverUserError() {
        var response = new CaptchaResponse(false, List.of("invalid-input-response", "invalid-input-secret"));

        assertThat(response.verdict()).isEqualTo(CaptchaVerdict.MISCONFIGURED);
    }

    @Test
    void nullCodesBecomeAnEmptyList() {
        assertThat(new CaptchaResponse(false, null).errorCodes()).isEmpty();
    }

    @Test
    void codesAreCopiedAndImmutable() {
        List<String> source = new ArrayList<>(List.of("a"));
        var response = new CaptchaResponse(false, source);
        source.add("b");

        assertThat(response.errorCodes()).containsExactly("a");
        assertThatThrownBy(() -> response.errorCodes().add("c")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptedIsASuccessWithoutCodes() {
        var accepted = CaptchaResponse.accepted();

        assertThat(accepted.success()).isTrue();
        assertThat(accepted.errorCodes()).isEmpty();
        assertThat(accepted.verdict()).isEqualTo(CaptchaVerdict.SOLVED);
    }
}
