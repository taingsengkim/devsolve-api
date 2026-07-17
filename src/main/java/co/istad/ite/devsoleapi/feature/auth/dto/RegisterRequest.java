package co.istad.ite.devsoleapi.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
        @NotBlank(message = "Username is required")
        String username,
        @NotBlank(message = "Password is required")
        String password,
        @NotBlank(message = "Confirm Password is required")
        String confirmPassword,
        String email,
        @NotBlank(message = "First name is required")
        String firstName,
        @NotBlank(message = "Last name is required")
        String lastName,
        @NotBlank
        @Pattern(regexp = "^\\d{9,11}$", message = "Phone number must contain 9 to 11 digits")
        String phone
) {
}