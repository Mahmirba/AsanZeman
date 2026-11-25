package com.sb24.asanzeman.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.shared.model.DomainException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.context.request.ServletWebRequest;
import tools.jackson.databind.ObjectMapper;

class ProblemDetailSecurityHandlersTest {

    private final ProblemDetailSecurityHandlers handlers = new ProblemDetailSecurityHandlers(
            new ProblemDetailExceptionHandler(messages()), new ObjectMapper());

    private static ResourceBundleMessageSource messages() {
        var ms = new ResourceBundleMessageSource();
        ms.setBasename("messages");
        ms.setDefaultEncoding("UTF-8");
        return ms;
    }

    @Test
    void entryPointWrites401ProblemJson() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/branch/requests");
        var response = new MockHttpServletResponse();

        handlers.commence(request, response, new InsufficientAuthenticationException("none"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString())
                .contains("\"status\":401")
                .contains("\"code\":\"auth.unauthenticated\"")
                .contains("\"instance\":\"/api/branch/requests\"")
                .contains("وارد سامانه شوید");
    }

    @Test
    void accessDeniedWrites403ProblemJson() throws Exception {
        var response = new MockHttpServletResponse();

        handlers.handle(new MockHttpServletRequest("POST", "/api/admin/agencies"), response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"access.denied\"");
    }

    @Test
    void frameworkErrorWithoutBodyStillGetsTheProblemShape() {
        var handler = new ProblemDetailExceptionHandler(messages());
        var response = handler.handleExceptionInternal(new IllegalStateException("x"), null,
                new HttpHeaders(), HttpStatus.NOT_ACCEPTABLE,
                new ServletWebRequest(new MockHttpServletRequest()));
        var body = (ProblemDetail) response.getBody();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(body.getProperties()).containsEntry("code", "http.406");
        assertThat(body.getDetail()).isEqualTo("قالب درخواستی پشتیبانی نمی‌شود.");
    }

    @Test
    void statusMappingCoversEveryKind() {
        assertThat(ProblemDetailExceptionHandler.statusOf(DomainException.Kind.TEMPORARILY_UNAVAILABLE))
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ProblemDetailExceptionHandler.statusOf(DomainException.Kind.UNAUTHORIZED))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        var problem = new ProblemDetailExceptionHandler(messages())
                .build(HttpStatus.CONFLICT, "service.temporarily-unavailable", "/x", List.of("AAA"));
        assertThat(problem.getDetail()).contains("AAA");
        assertThat(problem.getProperties()).containsEntry("code", "service.temporarily-unavailable");
    }
}
