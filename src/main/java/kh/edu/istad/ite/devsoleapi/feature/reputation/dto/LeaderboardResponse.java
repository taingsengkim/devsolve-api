package kh.edu.istad.ite.devsoleapi.feature.reputation.dto;

import java.util.UUID;

public record LeaderboardResponse(
        Integer rank,

        UUID id,

        String fullName,

        String avatarUrl,

        String country,

        Integer reputation,

        Integer totalReports,

        Integer validReports,

        Integer criticalReports,

        Integer recognitionCount
) {
}
