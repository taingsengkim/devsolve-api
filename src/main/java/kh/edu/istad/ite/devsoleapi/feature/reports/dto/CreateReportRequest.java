package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;

import java.util.UUID;

public record CreateReportRequest(
        @NotBlank(message = "Title is required")
        @Size(
                max = 255,
                message = "Title must not exceed 255 characters"
        )
        String title,

        @NotBlank(message = "Vulnerability information is required")
        String vulnerabilityInformation,

        String impact,

        @NotNull(message = "Reported severity is required")
        @JsonAlias("severity")
        Severity reportedSeverity,

        UUID weaknessId,

        UUID assetId
) {
}
