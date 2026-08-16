package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportEnvironment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

        @Size(
                max = 20000,
                message = "Steps to reproduce must not exceed 20000 characters"
        )
        String stepsToReproduce,

        @Size(
                max = 20000,
                message = "Proof of concept must not exceed 20000 characters"
        )
        @JsonAlias("proofOfConceptDetails")
        String proofOfConcept,

        @Size(
                max = 10000,
                message = "Remediation recommendation must not exceed 10000 "
                        + "characters"
        )
        String remediationRecommendation,

        @Size(
                max = 1000,
                message = "Target endpoint must not exceed 1000 characters"
        )
        @JsonAlias({"endpoint", "assetUrl"})
        String targetEndpoint,

        ReportEnvironment environment,

        @PastOrPresent(
                message = "Discovery date cannot be in the future"
        )
        LocalDateTime discoveredAt,

        @Size(
                max = 10,
                message = "A report cannot carry more than 10 reference links"
        )
        List<
                @Size(
                        max = 500,
                        message = "A reference link must not exceed 500 "
                                + "characters"
                )
                String
        > referenceLinks,

        @NotNull(message = "Reported severity is required")
        @JsonAlias("severity")
        Severity reportedSeverity,

        @Size(
                max = 255,
                message = "CVSS vector must not exceed 255 characters"
        )
        String cvssVector,

        @DecimalMin(
                value = "0.0",
                message = "CVSS score must be between 0.0 and 10.0"
        )
        @DecimalMax(
                value = "10.0",
                message = "CVSS score must be between 0.0 and 10.0"
        )
        @Digits(
                integer = 2,
                fraction = 1,
                message = "CVSS score takes at most one decimal place"
        )
        BigDecimal cvssScore,

        UUID weaknessId,

        UUID assetId
) {
}
