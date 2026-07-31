package kh.edu.istad.ite.devsoleapi.feature.userprofile.dto;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.GenderStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

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
        List<SocialLinkResponse> socialLinks,
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
