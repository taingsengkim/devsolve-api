package kh.edu.istad.ite.devsoleapi.feature.userprofile.domain;

import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * The profile's public handle, stored in the case its owner chose and
     * unique without regard to case — the unique index is on
     * {@code lower(username)}, which {@code @Column(unique = true)} cannot
     * express, so it lives in schema.sql.
     */
    @NotBlank(message = "Username is required")
    @Size(
            min = 3,
            max = 30,
            message = "Username must be between 3 and 30 characters"
    )
    @Pattern(
            regexp = "^[a-zA-Z0-9](?:[a-zA-Z0-9._-]{1,28}[a-zA-Z0-9])?$",
            message = "Username must start and end with a letter or number, "
                    + "and may contain dots, underscores and hyphens between"
    )
    @Column(length = 30, nullable = false)
    private String username;

    /**
     * When the handle last moved, or null while it is still the one the
     * account was created with. Null therefore reads as "never changed", which
     * is what makes a first change free.
     */
    @Column(name = "username_changed_at")
    private LocalDateTime usernameChangedAt;

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

    /** The banner behind the avatar on a profile page. Null until one is set. */
    @Size(max = 2048, message = "Cover image URL is too long")
    @Column(name = "cover_image_url", columnDefinition = "TEXT")
    private String coverImageUrl;

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

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("platform ASC")
    @BatchSize(size = 50)
    private List<UserSocialLink> socialLinks = new ArrayList<>();
}
