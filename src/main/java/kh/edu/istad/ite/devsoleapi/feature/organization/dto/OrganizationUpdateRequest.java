package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;

public record OrganizationUpdateRequest(
        @Size(max = 255, message = "Organization name must not exceed 255 characters")
        @Pattern(regexp = ".*\\S.*", message = "Organization name must not be blank")
        String name,

        @Size(max = 500, message = "Website URL must not exceed 500 characters")
        String websiteUrl,

        @Size(max = 500, message = "Logo URL must not exceed 500 characters")
        String logoUrl,

        @Size(
                max = 500,
                message = "Cover image URL must not exceed 500 characters"
        )
        String coverImageUrl,

        String description,

        Industry industry
) {
}
