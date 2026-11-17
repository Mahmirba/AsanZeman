package com.sb24.asanzeman.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Accepts an incoming {@code X-Correlation-Id} (or creates one), echoes it on the response and puts
 * it in the MDC so every log line and outbound call of the request carries it (8-4-2).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String id = sanitise(request.getHeader(HEADER));
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** Keeps a caller-supplied id only if it is short and printable; otherwise generates a new one. */
    static String sanitise(String incoming) {
        if (incoming == null || incoming.isBlank() || incoming.length() > MAX_LENGTH
                || !incoming.chars().allMatch(c -> c > 32 && c < 127)) {
            return UUID.randomUUID().toString();
        }
        return incoming;
    }
}
