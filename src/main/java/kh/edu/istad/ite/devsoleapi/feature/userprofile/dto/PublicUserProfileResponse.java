package kh.edu.istad.ite.devsoleapi.feature.userprofile.dto;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

public record PublicUserProfileResponse(
        UUID id,
        String username,
        String fullName,
        String biography,
        String avatarUrl,
        String country,
        List<SocialLinkResponse> socialLinks,
        int reputation,
        int totalReports,
        int validReports,
        int criticalReports,
        int recognitionCount,
        LocalDateTime joinedAt
) {
}
