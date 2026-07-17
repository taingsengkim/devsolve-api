package co.istad.ite.devsoleapi.feature.auth.dto;

import lombok.Builder;

@Builder
public record RegisterResponse(
        String userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone
) {
}
