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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "recognition_id",
            nullable = false,
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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}