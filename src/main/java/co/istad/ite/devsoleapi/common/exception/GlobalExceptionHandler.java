package co.istad.ite.devsoleapi.common.exception;

import io.minio.Http;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public RestErrorResponse handleResponseStatusException(ResponseStatusException e) {
        return buildError(
                HttpStatus.valueOf(e.getStatusCode().value()),
                e.getMessage(),
                null
        );
    }


    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public RestErrorResponse handleResourceNotFound(ResourceNotFoundException e) {
        return buildError(
                HttpStatus.NOT_FOUND,
                e.getMessage(),
                null
        );
    }

    private RestErrorResponse buildError(HttpStatus status, String message, Object details) {
        return RestErrorResponse.builder()
                .message(message)
                .code(status.value())
                .status(status.getReasonPhrase())
                .timestamp(Instant.now())
                .errorDetails(details)

                .build();
    }

}
