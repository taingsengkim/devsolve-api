package kh.edu.istad.ite.devsoleapi.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The caller's address, as seen from behind the reverse proxy.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    /**
     * @return the first hop of {@code X-Forwarded-For}, else the socket
     *         address, else null when no request is bound
     */
    public static String current() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes()
                instanceof ServletRequestAttributes attributes
                ? attributes.getRequest()
                : null;
    }
}
