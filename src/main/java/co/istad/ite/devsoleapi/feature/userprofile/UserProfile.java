package co.istad.ite.devsoleapi.feature.userprofile;

import co.istad.ite.devsoleapi.common.entity.BasedEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
public class UserProfile extends BasedEntity {

    @Id
    @NotNull(message = "ID is required")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 255)
    @Column(nullable = false, unique = true, length = 255)
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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private GenderStatus gender;

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    @Column(length = 100)
    private String country;

    @NotNull(message = "Status is required")
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "membership_status_enum")
    private UserStatus status = UserStatus.ACTIVE;

    @Min(value = 0, message = "Reputation cannot be negative")
    @Column(nullable = false)
    private int reputation;

    @Min(value = 0, message = "Total reports cannot be negative")
    @Column(name = "total_reports", nullable = false)
    private int totalReports;

    @Min(value = 0, message = "Valid reports cannot be negative")
    @Column(name = "valid_reports", nullable = false)
    private int validReports;

    @Min(value = 0, message = "Critical reports cannot be negative")
    @Column(name = "critical_reports", nullable = false)
    private int criticalReports;

    @Min(value = 0, message = "Recognition count cannot be negative")
    @Column(name = "recognition_count", nullable = false)
    private int recognitionCount;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
}
