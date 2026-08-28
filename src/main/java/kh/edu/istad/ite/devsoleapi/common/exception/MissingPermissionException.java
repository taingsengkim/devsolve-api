package kh.edu.istad.ite.devsoleapi.common.exception;

import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * A refusal that names the permission the caller was missing.
 *
 * <p>A bare 403 tells a client that something is not allowed but not what
 * would allow it, which leaves it unable to tell "you may not see this" apart
 * from "there is nothing here" — so it renders an empty screen instead of an
 * honest one. The permission travels in {@code errorDetails.requiredPermission}
 * so the caller can hide the entry rather than offer an action the API then
 * rejects.
 */
public class MissingPermissionException extends DetailedApiException {

    private final String requiredPermission;

    public MissingPermissionException(
            String requiredPermission,
            String reason
    ) {
        super(
                HttpStatus.FORBIDDEN,
                reason,
                Map.of("requiredPermission", requiredPermission)
        );
        this.requiredPermission = requiredPermission;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }
}
