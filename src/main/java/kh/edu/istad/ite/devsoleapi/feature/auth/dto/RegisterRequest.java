package kh.edu.istad.ite.devsoleapi.feature.auth.dto;

import kh.edu.istad.ite.devsoleapi.feature.auth.RoleEnum;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;


public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, numbers, dots, underscores, and hyphens")
        String username,

        @NotBlank(message = "Password is required")
//        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        String password,

//        @NotBlank(message = "Confirm password is required")
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
