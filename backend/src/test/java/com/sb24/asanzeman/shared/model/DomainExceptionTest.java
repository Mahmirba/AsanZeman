package com.sb24.asanzeman.shared.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {

    static final class SampleException extends DomainException {
        SampleException() {
            super("sample.code", Kind.CONFLICT, "a", 2);
        }
    }

    @Test
    void carriesCodeKindAndArgs() {
        var ex = new SampleException();
        assertThat(ex.code()).isEqualTo("sample.code");
        assertThat(ex.kind()).isEqualTo(DomainException.Kind.CONFLICT);
        assertThat(ex.args()).containsExactly("a", 2);
        assertThat(ex).hasMessage("sample.code").hasNoCause();
    }

    @Test
    void temporarilyUnavailableKeepsCauseAndServiceName() {
        var cause = new RuntimeException("timeout");
        var ex = new TemporarilyUnavailableException("Notifier", cause);
        assertThat(ex.kind()).isEqualTo(DomainException.Kind.TEMPORARILY_UNAVAILABLE);
        assertThat(ex.service()).isEqualTo("Notifier");
        assertThat(ex.code()).isEqualTo(TemporarilyUnavailableException.CODE);
        assertThat(ex).hasCause(cause);
    }
}
