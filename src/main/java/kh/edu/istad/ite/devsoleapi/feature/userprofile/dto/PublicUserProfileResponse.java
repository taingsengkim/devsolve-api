package kh.edu.istad.ite.devsoleapi.feature.userprofile.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PublicUserProfileResponse(
        UUID id,
        String fullName,
        String biography,
        String avatarUrl,
        String country,
        int reputation,
        int totalReports,
        int validReports,
        int criticalReports,
        int recognitionCount,
        LocalDateTime joinedAt
) {
}
