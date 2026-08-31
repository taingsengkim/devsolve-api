package kh.edu.istad.ite.devsoleapi.feature.virustotal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VirusTotalUrlScanRequest(
        @NotBlank(message = "URL is required")
        @Size(max = 2_048, message = "URL cannot exceed 2,048 characters")
        String url
) {
}
