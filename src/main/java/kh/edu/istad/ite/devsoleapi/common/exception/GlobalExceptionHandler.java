package kh.edu.istad.ite.devsoleapi.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<RestErrorResponse> handleResponseStatusException(
            ResponseStatusException exception
    ) {
        HttpStatus status = HttpStatus.valueOf(
                exception.getStatusCode().value()
        );
        return ResponseEntity.status(status).body(buildError(
                status,
                exception.getReason(),
                null
        ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<RestErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception
    ) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return ResponseEntity.status(status).body(buildError(
                status,
                exception.getMessage(),
                null
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestErrorResponse> handleInvalidRequest(
            MethodArgumentNotValidException exception
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(
                        error.getField(),
                        error.getDefaultMessage()
                )
        );

        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(buildError(
                status,
                "Request validation failed",
                fieldErrors
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RestErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception
    ) {
        Map<String, String> violations = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation ->
                violations.put(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                )
        );

        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(buildError(
                status,
                "Request validation failed",
                violations
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<RestErrorResponse> handleMaxUploadSize(
            MaxUploadSizeExceededException exception
    ) {
        HttpStatus status = HttpStatus.CONTENT_TOO_LARGE;
        return ResponseEntity.status(status).body(buildError(
                status,
                "Attachment cannot exceed 10 MiB",
                null
        ));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<RestErrorResponse> handleOptimisticLock(
            OptimisticLockingFailureException exception
    ) {
        HttpStatus status = HttpStatus.PRECONDITION_FAILED;
        return ResponseEntity.status(status).body(buildError(
                status,
                "The resource changed after it was read; fetch it again",
                null
        ));
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

        HttpStatus status = HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status).body(buildError(
                status,
                "The identity provider could not be reached or refused the "
                        + "request",
                null
        ));
    }

    /**
     * Last resort. Without it an unhandled exception fell through to Spring
     * Boot's default error page, which answers in a different shape from every
     * other error this API returns and logs nothing that names the request. The
     * stack trace here is the only record of what actually broke, so it is
     * logged with the path that triggered it; the client is told nothing about
     * our internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        // Spring raises its own typed exceptions for malformed bodies, wrong
        // methods and unsupported media types, each already carrying the right
        // status. Catching everything would otherwise flatten those 400s into
        // 500s, so let them keep the status they came with.
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(
                    errorResponse.getStatusCode().value()
            );
            String detail = errorResponse.getBody().getDetail();
            return ResponseEntity.status(status).body(buildError(
                    status,
                    detail != null ? detail : status.getReasonPhrase(),
                    null
            ));
        }

        log.error(
                "Unhandled exception serving {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(buildError(
                status,
                "Something went wrong on our side. Please try again.",
                null
        ));
    }

    private RestErrorResponse buildError(
            HttpStatus status,
            String message,
            Object details
    ) {
        return RestErrorResponse.builder()
                .message(message)
                .code(status.value())
                .status(status.getReasonPhrase())
                .timestamp(Instant.now())
                .errorDetails(details)
                .build();
    }
}
