package kh.edu.istad.ite.devsoleapi.feature.auth.dto;

import kh.edu.istad.ite.devsoleapi.feature.auth.RoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record RegisterRequest(
        // Matches UsernamePolicy: the handle minted here is the one that ends
        // up in profile URLs, so registration cannot accept a shape the rest
        // of the platform would later refuse.
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9](?:[a-zA-Z0-9._-]{1,28}[a-zA-Z0-9])?$",
                message = "Username must start and end with a letter or number, and may contain dots, underscores, and hyphens between"
        )
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @NotBlank(message = "Confirm password is required")
        @Size(min = 8, max = 100, message = "Confirm password must be between 8 and 100 characters")
        String confirmPassword,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "First name is required")
        @Size(max = 70, message = "First name must not exceed 70 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 70, message = "Last name must not exceed 70 characters")
        String lastName,

        @Size(max = 30, message = "Phone number must not exceed 30 characters")
        @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Phone number must be 8 to 15 digits, optionally starting with +")
        String phone,

        @NotNull(message = "Account type is required")
        RoleEnum accountType
) {
}
