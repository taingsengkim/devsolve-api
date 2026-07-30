package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import java.util.UUID;

public record ShowcaseViewCountResponse(
        UUID showcaseId,
        int viewCount
) {
}
