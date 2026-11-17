package com.sb24.asanzeman.shared.web;

import com.sb24.asanzeman.shared.model.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * D48: every error is an RFC 7807/9457 Problem Details body ({@code application/problem+json}).
 * Extensions: {@code code} (stable, machine-readable) and {@code violations} for validation.
 * {@code detail} is the Persian text from {@code messages_fa.properties}. Technical failures are
 * 503 with a generic message and the cause only in the server log.
 */
@RestControllerAdvice
public class ProblemDetailExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailExceptionHandler.class);
    static final String TYPE_PREFIX = "urn:asanzeman:error:";
    static final Locale FA = Locale.forLanguageTag("fa-IR");

    private final MessageSource messages;

    public ProblemDetailExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(DomainException ex, HttpServletRequest request) {
        HttpStatus status = statusOf(ex.kind());
        if (ex.kind() == DomainException.Kind.TEMPORARILY_UNAVAILABLE) {
            log.warn("bank service unavailable [{}]: {}", ex.code(), ex.args(), ex);
        } else {
            log.info("business error [{}] {}", ex.code(), ex.args());
        }
        ProblemDetail body = problem(status, ex.code(), message(ex.code(), ex.args().toArray()), request);
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail body = problem(HttpStatus.FORBIDDEN, "access.denied", message("access.denied"), request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail body = problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal.error", message("internal.error"), request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            violations.putIfAbsent(fe.getField(), resolve(fe.getDefaultMessage()));
        }
        ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message("validation.failed"));
        body.setType(URI.create(TYPE_PREFIX + "validation.failed"));
        body.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.setProperty("code", "validation.failed");
        body.setProperty("violations", violations);
        return ResponseEntity.badRequest().headers(headers).body(body);
    }

    @Override
    protected ProblemDetail createProblemDetail(Exception ex, HttpStatusCode status, String defaultDetail,
            String detailMessageCode, Object[] detailMessageArguments, WebRequest request) {
        // framework errors (bad JSON, wrong method, 404 route…): same shape, generic Persian detail
        ProblemDetail body = super.createProblemDetail(ex, status, defaultDetail, detailMessageCode, detailMessageArguments, request);
        String code = "http." + status.value();
        body.setType(URI.create(TYPE_PREFIX + code));
        body.setProperty("code", code);
        body.setDetail(message(code));
        return body;
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setType(URI.create(TYPE_PREFIX + code));
        body.setTitle(status.getReasonPhrase());
        body.setInstance(URI.create(request.getRequestURI()));
        body.setProperty("code", code);
        return body;
    }

    static HttpStatus statusOf(DomainException.Kind kind) {
        return switch (kind) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case TEMPORARILY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private String message(String code, Object... args) {
        return messages.getMessage(code, args, code, FA);
    }

    /** Bean Validation messages arrive either as {@code {key}} templates or already-resolved text. */
    private String resolve(String defaultMessage) {
        String msg = Objects.requireNonNullElse(defaultMessage, "validation.invalid");
        if (msg.startsWith("{") && msg.endsWith("}")) {
            return message(msg.substring(1, msg.length() - 1));
        }
        return msg;
    }

    /** Exposed for tests and for adapters that build problem bodies outside MVC (security entry points). */
    public ProblemDetail build(HttpStatus status, String code, String path, List<Object> args) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, message(code, args.toArray()));
        body.setType(URI.create(TYPE_PREFIX + code));
        body.setTitle(status.getReasonPhrase());
        body.setInstance(URI.create(path));
        body.setProperty("code", code);
        return body;
    }
}
