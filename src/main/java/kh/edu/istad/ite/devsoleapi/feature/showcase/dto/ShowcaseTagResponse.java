package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import java.util.UUID;

public record ShowcaseTagResponse(
        UUID id,
        String name,
        String slug
) {
}
