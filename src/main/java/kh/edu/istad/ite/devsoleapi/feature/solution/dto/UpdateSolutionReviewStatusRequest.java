package kh.edu.istad.ite.devsoleapi.feature.solution.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;

public record UpdateSolutionReviewStatusRequest(

        @NotNull(message = "Review status is required")
        ReviewStatus reviewStatus,

        @Size(
                max = 2000,
                message = "Rejection reason must not exceed 2000 characters"
        )
        String rejectionReason
) {
}
