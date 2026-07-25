package co.istad.ite.devsoleapi.feature.userprofile.dto;

import co.istad.ite.devsoleapi.feature.userprofile.GenderStatus;
import co.istad.ite.devsoleapi.feature.userprofile.UserStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record UserProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String biography,
        String phone,
        String avatarUrl,
        LocalDate dateOfBirth,
        GenderStatus gender,
        String country,
        UserStatus status,
        int reputation,
        int totalReports,
        int validReports,
        int criticalReports,
        int recognitionCount,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
