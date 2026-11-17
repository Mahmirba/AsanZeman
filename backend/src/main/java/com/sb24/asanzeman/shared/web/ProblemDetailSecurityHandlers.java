package com.sb24.asanzeman.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The security filter chain runs before MVC, so 401/403 raised there never reach the controller
 * advice. These handlers write the same RFC 7807 body (D48).
 */
@Component
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemDetailExceptionHandler problems;
    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandlers(ProblemDetailExceptionHandler problems, ObjectMapper objectMapper) {
        this.problems = problems;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        write(response, problems.build(HttpStatus.UNAUTHORIZED, "auth.unauthenticated", request.getRequestURI(), List.of()));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, problems.build(HttpStatus.FORBIDDEN, "access.denied", request.getRequestURI(), List.of()));
    }

    private void write(HttpServletResponse response, ProblemDetail body) throws IOException {
        response.setStatus(body.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
