package kh.edu.istad.ite.devsoleapi.feature.userprofile.dto;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.GenderStatus;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateUserProfileRequest(
        @Size(max = 70, message = "First name must not exceed 70 characters")
        @Pattern(regexp = ".*\\S.*", message = "First name must not be blank")
        String firstName,

        @Size(max = 70, message = "Last name must not exceed 70 characters")
        @Pattern(regexp = ".*\\S.*", message = "Last name must not be blank")
        String lastName,

        @Size(max = 300, message = "Biography cannot exceed 300 characters")
        String biography,

        @Size(max = 30, message = "Phone number cannot exceed 30 characters")
        @Pattern(
                regexp = "^\\+?[0-9]{8,15}$",
                message = "Phone number must be 8 to 15 digits, optionally starting with +"
        )
        String phone,

        @Size(max = 2048, message = "Avatar URL is too long")
        String avatarUrl,

        @Past(message = "Date of birth must be in the past")
        LocalDate dateOfBirth,

        GenderStatus gender,

        @Size(max = 100, message = "Country cannot exceed 100 characters")
        String country
) {
}
