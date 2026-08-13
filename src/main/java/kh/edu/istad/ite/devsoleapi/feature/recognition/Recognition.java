package kh.edu.istad.ite.devsoleapi.feature.recognition;

import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "recognitions",
        // One recognition per report. Without this a triager could award the
        // same finding repeatedly and each award would add reputation again,
        // which is a free points loop. The service checks first for a clean
        // 409, but two concurrent awards both pass that check, so the database
        // has to be the one that says no.
        uniqueConstraints = @UniqueConstraint(
                name = "uq_recognitions_report",
                columnNames = "report_id"
        ),
        indexes = {
                @Index(
                        name = "idx_recognitions_user_id",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_recognitions_program_id",
                        columnList = "program_id"
                )
        }
)
@Getter
@Setter
public class Recognition extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotNull(message = "Program ID is required")
    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @NotNull(message = "Report ID is required")
    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Awarded by is required")
    @Column(name = "awarded_by", nullable = false)
    private UUID awardedBy;

    @NotNull(message = "Awarded at is required")
    @Column(name = "awarded_at", nullable = false)
    private LocalDateTime awardedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "severity", nullable = false, columnDefinition = "severity_enum")
    private Severity severity;


}