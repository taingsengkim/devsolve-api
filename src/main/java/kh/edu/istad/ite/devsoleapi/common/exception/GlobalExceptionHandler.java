package kh.edu.istad.ite.devsoleapi.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns every failure into the one error shape this API answers with.
 *
 * <p>The catch-all at the bottom is what makes the rest of this class
 * necessary. An {@code @ExceptionHandler(Exception.class)} sits in front of
 * every mechanism Spring would otherwise use to answer — the security filter
 * chain's 403, the framework's own 400s — because the dispatcher resolves the
 * exception here and never rethrows it. Anything that deserves a status of its
 * own therefore has to be named explicitly below, or it silently becomes a
 * 500. That is not a hypothetical: {@code @PreAuthorize} denials and malformed
 * path variables both used to leave here as "Something went wrong on our
 * side."
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ---------------------------------------------------------------- security

    /**
     * Method security throws {@code AuthorizationDeniedException}, a subclass
     * of this. Normally {@code ExceptionTranslationFilter} would answer it,
     * but it never gets the chance: the exception is raised inside the
     * controller, so the dispatcher hands it to this advice first.
     *
     * <p>A caller who is not authenticated at all gets 401 rather than 403 —
     * telling an anonymous client "forbidden" hides the fact that logging in
     * would fix it, which is the distinction the filter chain draws too.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RestErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        if (isAnonymous()) {
            return respond(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required to access this resource",
                    null,
                    request
            );
        }

        return respond(
                HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action",
                null,
                request
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<RestErrorResponse> handleAuthenticationFailure(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.UNAUTHORIZED,
                "A valid access token is required",
                null,
                request
        );
    }

    // ------------------------------------------------------- explicit statuses

    /**
     * More specific than the {@link ResponseStatusException} handler below,
     * which Spring resolves in its favour, so the details survive instead of
     * being flattened into a status and a sentence.
     */
    @ExceptionHandler(DetailedApiException.class)
    public ResponseEntity<RestErrorResponse> handleDetailedApiFailure(
            DetailedApiException exception,
            HttpServletRequest request
    ) {
        return respond(
                exception.getStatusCode(),
                exception.getReason(),
                exception.getErrorDetails(),
                request
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<RestErrorResponse> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request
    ) {
        HttpStatusCode status = exception.getStatusCode();

        // getReason() is null for the status-only constructors, which used to
        // put "message": null on the wire.
        String reason = exception.getReason() != null
                ? exception.getReason()
                : reasonPhrase(status);

        return respond(status, reason, null, request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<RestErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.NOT_FOUND,
                exception.getMessage(),
                null,
                request
        );
    }

    // ------------------------------------------------------ malformed requests

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestErrorResponse> handleInvalidRequest(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()
                )
        );

        return respond(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                fieldErrors,
                request
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RestErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                violationsOf(exception),
                request
        );
    }

    /**
     * A path variable or query parameter that will not convert — most often a
     * malformed UUID. The client needs to know which one and what shape it
     * should have been; it does not need the converter's internals.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RestErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        Class<?> required = exception.getRequiredType();
        String expected = required != null
                ? required.getSimpleName()
                : "the expected type";

        return respond(
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                Map.of(
                        exception.getName(),
                        "must be a valid " + expected
                ),
                request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RestErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        // The exception message quotes the parser and the input stream class;
        // neither is the client's business.
        log.debug("Unreadable request body on {}", request.getRequestURI(),
                exception);

        return respond(
                HttpStatus.BAD_REQUEST,
                "The request body is missing or is not valid JSON",
                null,
                request
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<RestErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.CONTENT_TOO_LARGE,
                "Attachment cannot exceed 10 MiB",
                null,
                request
        );
    }

    // --------------------------------------------------------- persistence

    /**
     * A unique or foreign key breach. Almost always a duplicate submit or two
     * writers racing for the same row, both of which are the caller's answer
     * to give — not a server fault. The database's message names the
     * constraint and often the colliding value, so it is logged rather than
     * returned.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RestErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        log.warn(
                "Constraint violation serving {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        return respond(
                HttpStatus.CONFLICT,
                "That change conflicts with data that already exists",
                null,
                request
        );
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<RestErrorResponse> handleOptimisticLock(
            OptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        return respond(
                HttpStatus.PRECONDITION_FAILED,
                "The resource changed after it was read; fetch it again",
                null,
                request
        );
    }

    /**
     * Bean validation on an entity fails at flush, not at bind, so it arrives
     * wrapped in a transaction failure long after the controller returned.
     * Unwrapped it is the same 400 the request should have got in the first
     * place; left alone it is an opaque 500.
     */
    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<RestErrorResponse> handleTransactionFailure(
            TransactionSystemException exception,
            HttpServletRequest request
    ) {
        Throwable cause = exception.getMostSpecificCause();

        if (cause instanceof ConstraintViolationException violation) {
            return respond(
                    HttpStatus.BAD_REQUEST,
                    "Request validation failed",
                    violationsOf(violation),
                    request
            );
        }

        return unexpected(exception, request);
    }

    /**
     * The Keycloak admin client speaks JAX-RS, so a realm the service account
     * cannot read, or a user it cannot see, arrives here as a
     * {@link WebApplicationException} rather than anything Spring understands.
     * Left unmapped these became bare 500s, which is how a working profile read
     * turned into "Internal Server Error" with nothing to go on.
     */
    @ExceptionHandler(WebApplicationException.class)
    public ResponseEntity<RestErrorResponse> handleIdentityProviderFailure(
            WebApplicationException exception,
            HttpServletRequest request
    ) {
        log.error(
                "Keycloak admin API refused {} with status {}",
                request.getRequestURI(),
                exception.getResponse() == null
                        ? "unknown"
                        : exception.getResponse().getStatus(),
                exception
        );

        return respond(
                HttpStatus.BAD_GATEWAY,
                "The identity provider could not be reached or refused the "
                        + "request",
                null,
                request
        );
    }

    // ------------------------------------------------------------- catch-all

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        // Spring raises its own typed exceptions for wrong methods and
        // unsupported media types, each already carrying the right status.
        // Catching everything would otherwise flatten those into 500s, so let
        // them keep the status they came with.
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            String detail = errorResponse.getBody().getDetail();

            if (status.is5xxServerError()) {
                return unexpected(exception, request);
            }

            return respond(
                    status,
                    detail != null ? detail : reasonPhrase(status),
                    null,
                    request
            );
        }

        return unexpected(exception, request);
    }

    /**
     * The client is told nothing about our internals, so the only way a user
     * report can be tied back to the stack trace is the trace id printed in
     * both places.
     */
    private ResponseEntity<RestErrorResponse> unexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);

        log.error(
                "Unhandled exception serving {} {} [traceId={}]",
                request.getMethod(),
                request.getRequestURI(),
                traceId,
                exception
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(RestErrorResponse.builder()
                        .message("Something went wrong on our side. Please "
                                + "try again.")
                        .code(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .status(HttpStatus.INTERNAL_SERVER_ERROR
                                .getReasonPhrase())
                        .timestamp(Instant.now())
                        .path(request.getRequestURI())
                        .traceId(traceId)
                        .build());
    }

    // ------------------------------------------------------------- plumbing

    private ResponseEntity<RestErrorResponse> respond(
            HttpStatusCode status,
            String message,
            Object details,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(RestErrorResponse.builder()
                .message(message)
                .code(status.value())
                .status(reasonPhrase(status))
                .timestamp(Instant.now())
                .errorDetails(details)
                .path(request != null ? request.getRequestURI() : null)
                .build());
    }

    /**
     * {@code HttpStatus.valueOf} throws on any code outside the standard set,
     * and a throw in here escapes the advice as a bodyless 500 — the one
     * failure mode an error handler must not have.
     */
    private String reasonPhrase(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }

    private Map<String, String> violationsOf(
            ConstraintViolationException exception
    ) {
        Map<String, String> violations = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                violations.putIfAbsent(
                        propertyNameOf(violation),
                        violation.getMessage()
                )
        );
        return violations;
    }

    /**
     * Validated method parameters carry a path of {@code methodName.paramName}.
     * The method name is an implementation detail of ours and means nothing to
     * the caller, who only sent the parameter — so a rejected {@code ?size=101}
     * is reported as "size", not "getLeaderboard.size". Nested bean paths keep
     * every node that the client actually supplied.
     */
    private String propertyNameOf(ConstraintViolation<?> violation) {
        StringBuilder name = new StringBuilder();

        for (Path.Node node : violation.getPropertyPath()) {
            if (node.getKind() == ElementKind.METHOD
                    || node.getKind() == ElementKind.CONSTRUCTOR
                    || node.getKind() == ElementKind.BEAN) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append('.');
            }
            name.append(node.getName());
        }

        return name.isEmpty()
                ? violation.getPropertyPath().toString()
                : name.toString();
    }

    private boolean isAnonymous() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }
}
