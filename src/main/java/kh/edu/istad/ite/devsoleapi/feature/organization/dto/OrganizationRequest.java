package kh.edu.istad.ite.devsoleapi.feature.organization.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;

public record OrganizationRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
        String fullName,

        @NotBlank(message = "Job title is required")
        @Size(max = 100, message = "Job title must not exceed 100 characters")
        String jobTitle,

        @NotBlank(message = "Business email is required")
        @Email(message = "Business email must be valid")
        @Size(max = 255, message = "Business email must not exceed 255 characters")
        String email,

        /*
         * Accepted as people write it — "+855 12 345 678", "(023) 999-111" —
         * and reduced to digits before it is stored. The stored column is
         * stricter than this, so the shape is checked here and the digit count
         * once the punctuation is gone.
         */
        @NotBlank(message = "Phone number is required")
        @Size(max = 30, message = "Phone number must not exceed 30 characters")
        @Pattern(
                regexp = "^\\+?[0-9(][0-9\\s().-]*[0-9]$",
                message = "Phone number may contain digits, spaces, brackets, "
                        + "dots and hyphens, optionally starting with +"
        )
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @NotBlank(message = "Password confirmation is required")
        @Size(min = 8, max = 100, message = "Password confirmation must be between 8 and 100 characters")
        String confirmPassword,

        @NotBlank(message = "Company name is required")
        @Size(max = 255, message = "Company name must not exceed 255 characters")
        String companyName,

        @NotBlank(message = "Company website is required")
        @Size(max = 500, message = "Company website must not exceed 500 characters")
        String companyWebsite,

        @NotNull(message = "Industry is required")
        Industry industry,

        @NotBlank(message = "Company size is required")
        @Pattern(
                regexp = "^(1-10|11-50|51-200|201-500|501-1000|1000\\+)$",
                message = "Company size must be one of: 1-10, 11-50, 51-200, 201-500, 501-1000, 1000+"
        )
        String companySize,

        @NotBlank(message = "Country is required")
        @Size(max = 100, message = "Country must not exceed 100 characters")
        String country,

        @NotBlank(message = "Please explain why you are joining")
        @Size(max = 1000, message = "Joining reason must not exceed 1000 characters")
        String joiningReason
) {
}
