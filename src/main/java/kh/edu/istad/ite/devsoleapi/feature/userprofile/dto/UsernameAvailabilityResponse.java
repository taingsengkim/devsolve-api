package kh.edu.istad.ite.devsoleapi.feature.userprofile.dto;

/**
 * Whether a handle can be taken, answered while somebody is still typing it.
 *
 * <p>Exists so the refusal arrives beside the field rather than on submit,
 * after the rest of a profile edit has already been filled in.
 *
 * @param reason why not, in words meant for the person typing — null when the
 *               username is available. The four cases read differently to a
 *               user and a client should not have to infer them from a status
 *               code: too short, not a legal handle, reserved, already taken.
 */
public record UsernameAvailabilityResponse(
        String username,
        boolean available,
        String reason
) {

    public static UsernameAvailabilityResponse available(String username) {
        return new UsernameAvailabilityResponse(username, true, null);
    }

    public static UsernameAvailabilityResponse unavailable(
            String username,
            String reason
    ) {
        return new UsernameAvailabilityResponse(username, false, reason);
    }
}
