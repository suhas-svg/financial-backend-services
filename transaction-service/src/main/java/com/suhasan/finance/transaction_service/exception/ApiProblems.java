package com.suhasan.finance.transaction_service.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;

/**
 * Builds every error response as RFC 9457 (formerly 7807) Problem Details,
 * served as {@code application/problem+json}.
 *
 * <p>{@code type} is a stable URN per problem kind ({@code urn:financial:problem:<slug>}),
 * {@code instance} is the request path. The pre-RFC fields {@code error},
 * {@code message}, {@code path} and {@code timestamp} are kept as extension members so
 * existing clients keep working; new clients should read {@code title} and {@code detail}.
 */
public final class ApiProblems {

    public static final String TYPE_PREFIX = "urn:financial:problem:";

    // Same ProblemDetail mixin and ISO-8601 dates as the MVC message converters.
    private static final ObjectMapper WRITER = Jackson2ObjectMapperBuilder.json().build();

    private ApiProblems() {
    }

    public static ProblemDetail problem(final HttpStatus status, final String slug, final String title,
                                        final String detail, final HttpServletRequest request) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_PREFIX + slug));
        problem.setTitle(title);
        final String path = request == null ? null : request.getRequestURI();
        if (path != null) {
            try {
                problem.setInstance(new URI(null, null, path, null));
            } catch (URISyntaxException ignored) {
                // A path that is not a valid URI is still reported in "path" below.
            }
        }
        problem.setProperty("error", title);
        problem.setProperty("message", detail);
        problem.setProperty("path", path);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    public static ResponseEntity<ProblemDetail> response(final HttpStatus status, final String slug,
                                                         final String title, final String detail,
                                                         final HttpServletRequest request) {
        return response(problem(status, slug, title, detail, request));
    }

    public static ResponseEntity<ProblemDetail> response(final ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /** For filters and security handlers that run outside Spring MVC. */
    public static void write(final HttpServletResponse response, final ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        WRITER.writeValue(response.getOutputStream(), problem);
    }

    public static void write(final HttpServletResponse response, final HttpStatus status, final String slug,
                             final String title, final String detail, final HttpServletRequest request)
            throws IOException {
        write(response, problem(status, slug, title, detail, request));
    }
}
