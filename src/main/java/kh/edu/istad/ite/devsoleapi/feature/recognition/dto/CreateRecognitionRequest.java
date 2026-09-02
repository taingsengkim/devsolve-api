package kh.edu.istad.ite.devsoleapi.feature.recognition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * What an organization needs to say to credit a finding: which finding, and
 * what to call the credit.
 *
 * @param userId    who to credit. Optional, and only ever a hint — the credit
 *                  goes to whoever reported the report, which is the only
 *                  answer that can be right.
 * @param programId which program the finding was against. Optional for the
 *                  same reason: the report already says. Required here once,
 *                  it meant a client that restated it wrongly got a 404 for a
 *                  program the server could see perfectly well on the report.
 */
public record CreateRecognitionRequest(

        UUID userId,

        UUID programId,

        @NotNull(message = "Report ID is required")
        UUID reportId,

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        String title,

        @Size(max = 5000, message = "Description is too long")
        String description
) {
}