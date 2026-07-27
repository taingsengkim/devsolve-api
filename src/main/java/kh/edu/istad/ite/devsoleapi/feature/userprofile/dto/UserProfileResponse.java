package kh.edu.istad.ite.devsoleapi.feature.userprofile.dto;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserStatus;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record  UserProfileResponse(
        String id,
        String email,
        String fullName,
        String biography,
        String phone,
        String avatarUrl,
        LocalDate dateOfBirth,
        UserStatus status
) {
}
