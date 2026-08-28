package kh.edu.istad.ite.devsoleapi.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

/**
 * @param path       the request that failed, so a report does not have to be
 *                   correlated by timestamp alone
 * @param violations set on validation failures only, and alongside
 *                   {@code errorDetails} rather than instead of it — the map
 *                   stays what it was for existing clients, and this carries
 *                   the constraint that rejected each field for clients that
 *                   would otherwise keep their own copy of the rule
 * @param traceId    set on 5xx only, and printed with the stack trace in the
 *                   logs — the client is told nothing else about the failure,
 *                   so this is the only thread back to it
 */
@Builder
public record RestErrorResponse(
        String message,
        Integer code,
        String status,
        Instant timestamp,
        Object errorDetails,

        @Schema(nullable = true)
        List<FieldViolation> violations,

        String path,

        @Schema(nullable = true)
        String traceId
) {
}
