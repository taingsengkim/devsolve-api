package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import jakarta.validation.constraints.*;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.Severity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class CreateReportRequest {

    @NotBlank(message = "Title is required.")
    @Size(max = 255, message = "Title must not exceed 255 characters.")
    private String title;

    @NotBlank(message = "Vulnerability information is required.")
    private String vulnerabilityInformation;

    private String impact;

    @NotNull(message = "Severity is required.")
    private Severity severity;

    @DecimalMin(value = "0.0", message = "CVSS score must be at least 0.0.")
    @DecimalMax(value = "10.0", message = "CVSS score must not exceed 10.0.")
    private BigDecimal cvssScore;

    private UUID weaknessId;

    private AssetType assetType;
}