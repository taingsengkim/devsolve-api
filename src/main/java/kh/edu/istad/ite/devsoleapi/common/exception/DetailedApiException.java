package kh.edu.istad.ite.devsoleapi.common.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/**
 * A refusal that carries machine-readable details beside its status.
 *
 * <p>A status and a sentence tell a client that something was refused but not
 * what to do about it, which is how a client ends up parsing prose or showing
 * an empty screen it cannot explain. Anything the caller could act on — the
 * permission it lacked, the organizations it must choose between, the values
 * that were rejected — travels in {@code errorDetails} instead.
 */
public class DetailedApiException extends ResponseStatusException {

    private final transient Object errorDetails;

    public DetailedApiException(
            HttpStatusCode status,
            String reason,
            Object errorDetails
    ) {
        super(status, reason);
        this.errorDetails = errorDetails;
    }

    public Object getErrorDetails() {
        return errorDetails;
    }
}
