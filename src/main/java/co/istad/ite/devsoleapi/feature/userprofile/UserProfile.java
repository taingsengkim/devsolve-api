package co.istad.ite.devsoleapi.feature.userprofile;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
public class UserProfile {

    @Id
    @NotBlank(message = "ID is required")
    private String id;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255)
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
    @Column(length = 150, nullable = false)
    private String fullName;

    @Size(max = 300, message = "Biography cannot exceed 300 characters")
    @Column(length = 300)
    private String biography;

    @Pattern(
            regexp = "^\\+?[0-9]{8,15}$",
            message = "Phone number is invalid"
    )
    @Column(length = 30)
    private String phone;

    @Size(max = 2048, message = "Avatar URL is too long")
    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;

    @Past(message = "Date of birth must be in the past")
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private UserStatus status = UserStatus.ACTIVE;
}