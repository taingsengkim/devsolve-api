package kh.edu.istad.ite.devsoleapi.common.exception;

import lombok.Builder;

import java.time.Instant;

/**
 * @param path    the request that failed, so a report does not have to be
 *                correlated by timestamp alone
 * @param traceId set on 5xx only, and printed with the stack trace in the
 *                logs — the client is told nothing else about the failure, so
 *                this is the only thread back to it
 */
@Builder
public record RestErrorResponse(
        String message,
        Integer code,
        String status,
        Instant timestamp,
        Object errorDetails,
        String path,
        String traceId
) {
}
