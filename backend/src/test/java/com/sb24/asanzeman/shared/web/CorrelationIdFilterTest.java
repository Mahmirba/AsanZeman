package com.sb24.asanzeman.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void echoesAcceptableIncomingIdAndExposesItInMdcDuringTheRequest() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "abc-123");
        var response = new MockHttpServletResponse();
        var seen = new AtomicReference<String>();
        FilterChain chain = (req, res) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesAnIdWhenMissingOrUnsafe() throws Exception {
        assertThat(CorrelationIdFilter.sanitise(null)).hasSize(36);
        assertThat(CorrelationIdFilter.sanitise("  ")).hasSize(36);
        assertThat(CorrelationIdFilter.sanitise("x".repeat(65))).hasSize(36);
        assertThat(CorrelationIdFilter.sanitise("bad\nvalue")).hasSize(36);
        assertThat(CorrelationIdFilter.sanitise("caf\u00e9")).hasSize(36);
        assertThat(CorrelationIdFilter.sanitise("fine-id_1")).isEqualTo("fine-id_1");

        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> { });
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).hasSize(36);
    }

    @Test
    void clearsMdcEvenWhenTheChainThrows() {
        var request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "boom");
        try {
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> { throw new IllegalStateException("x"); });
        } catch (Exception expected) {
            // ignore
        }
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
