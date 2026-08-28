package kh.edu.istad.ite.devsoleapi.feature.program.dto;

/**
 * Whether a program handle can be taken, answered while somebody is still
 * typing it.
 *
 * <p>Exists so the refusal arrives beside the field rather than four wizard
 * steps later on submit. {@code GET /programs/handle/{handle}} cannot answer
 * this: it resolves published programs, so a draft holding the handle reads as
 * 404 and the client reports "available" for a name the write will reject.
 *
 * @param reason why not, in words meant for the person typing — null when the
 *               handle is available. The cases read differently to a user and
 *               a client should not have to infer them: not a legal handle,
 *               or already used by another program.
 */
public record ProgramHandleAvailabilityResponse(
        String handle,
        boolean available,
        String reason
) {

    public static ProgramHandleAvailabilityResponse available(String handle) {
        return new ProgramHandleAvailabilityResponse(handle, true, null);
    }

    public static ProgramHandleAvailabilityResponse unavailable(
            String handle,
            String reason
    ) {
        return new ProgramHandleAvailabilityResponse(handle, false, reason);
    }
}
