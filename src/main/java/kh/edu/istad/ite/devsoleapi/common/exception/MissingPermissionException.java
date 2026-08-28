package kh.edu.istad.ite.devsoleapi.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * A refusal that names the permission the caller was missing.
 *
 * <p>A bare 403 tells a client that something is not allowed but not what
 * would allow it, which leaves it unable to tell "you may not see this" apart
 * from "there is nothing here" — so it renders an empty screen instead of an
 * honest one. The permission travels in {@code errorDetails.requiredPermission}
 * so the caller can hide the entry rather than offer an action the API then
 * rejects.
 *
 * <p>Extends {@link ResponseStatusException} so that callers already catching
 * it — {@code OrganizationAuthorizationService.hasPermission} turns a refusal
 * into a boolean — keep working unchanged.
 */
public class MissingPermissionException extends ResponseStatusException {

    private final String requiredPermission;

    public MissingPermissionException(
            String requiredPermission,
            String reason
    ) {
        super(HttpStatus.FORBIDDEN, reason);
        this.requiredPermission = requiredPermission;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }
}
