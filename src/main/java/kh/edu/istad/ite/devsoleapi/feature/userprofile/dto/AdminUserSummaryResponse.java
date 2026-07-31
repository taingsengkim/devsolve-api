package kh.edu.istad.ite.devsoleapi.feature.userprofile.dto;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminUserSummaryResponse(
        UUID id,
        String fullName,
        String email,
        String avatarUrl,
        String country,
        UserStatus status,
        int reputation,
        int totalReports,
        int validReports,
        int criticalReports,
        int recognitionCount,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt
) {
}
