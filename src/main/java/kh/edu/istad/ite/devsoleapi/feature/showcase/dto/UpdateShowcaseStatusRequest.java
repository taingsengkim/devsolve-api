package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateShowcaseStatusRequest(
        @NotNull(message = "Review status is required")
        ReviewStatus reviewStatus,

        @Size(
                max = 2000,
                message = "Rejection reason must not exceed 2000 characters"
        )
        String rejectionReason
) {
}
