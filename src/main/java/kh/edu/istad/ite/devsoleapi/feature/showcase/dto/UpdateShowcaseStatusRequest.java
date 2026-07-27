package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateShowcaseStatusRequest(
        @NotNull(message = "Review status is required")
        ReviewStatus reviewStatus
) {
}
