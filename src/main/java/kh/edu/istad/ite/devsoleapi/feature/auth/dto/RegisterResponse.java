package kh.edu.istad.ite.devsoleapi.feature.auth.dto;

import kh.edu.istad.ite.devsoleapi.feature.auth.RoleEnum;
import lombok.Builder;

@Builder
public record RegisterResponse(
        String userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String phone,
        RoleEnum accountType
) {
}
