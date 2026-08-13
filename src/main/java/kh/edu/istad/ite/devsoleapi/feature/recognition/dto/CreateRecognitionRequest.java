package kh.edu.istad.ite.devsoleapi.feature.recognition.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateRecognitionRequest(

        @NotNull(message = "User ID is required")
        UUID userId,

        @NotNull(message = "Program ID is required")
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