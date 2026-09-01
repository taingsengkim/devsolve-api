package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import jakarta.persistence.*;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.recognition.Recognition;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "hacktivities",
        indexes = {
                @Index(
                        name = "idx_hacktivity_created_at",
                        columnList = "created_at"
                ),
                @Index(
                        name = "idx_hacktivity_user_id",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_hacktivity_organization_id",
                        columnList = "organization_id"
                ),
                @Index(
                        name = "idx_hacktivity_program_id",
                        columnList = "program_id"
                ),
                @Index(
                        name = "idx_hacktivity_event_type",
                        columnList = "event_type"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hacktivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The recognition this row is about, or null when the row is about
     * something else that happened to the report.
     *
     * <p>Every entry used to be a recognition, so this was mandatory. A
     * resolution and a disclosure are also things the feed should carry, and
     * neither has a recognition behind it — the report was fixed or published
     * whether or not anybody was credited for it.
     *
     * <p>Still unique: two recognitions cannot share a row. Postgres does not
     * count NULLs as equal in a unique index, so any number of rows may have
     * none.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "recognition_id",
            unique = true
    )
    private Recognition recognition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private UserProfile user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "organization_id",
            nullable = false
    )
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "report_id",
            nullable = false
    )
    private Report report;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "program_id",
            nullable = false
    )
    private Program program;

    /**
     * Kept as a plain varchar rather than a Postgres named enum like the
     * severity and state columns: those already existed as database types, and
     * adding a new one costs a CREATE TYPE that has to land before the column
     * on every environment. A widening of this enum is then an application
     * change alone.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    @Builder.Default
    private HacktivityEventType eventType =
            HacktivityEventType.RECOGNITION_AWARDED;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}