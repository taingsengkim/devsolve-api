package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import jakarta.persistence.*;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "organizations")
public class Organization extends BasedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner_job_title", nullable = false, length = 100)
    private String ownerJobTitle;

    @Column(name = "company_size", nullable = false, length = 20)
    private String companySize;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "joining_reason", nullable = false, length = 1000)
    private String joiningReason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "industry", columnDefinition = "industry_enum")
    private Industry industry;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 20
    )
    private OrganizationStatus status = OrganizationStatus.PENDING;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "submission_version", nullable = false)
    private int submissionVersion = 1;

    @Column(name = "verification_email_sent_at")
    private LocalDateTime verificationEmailSentAt;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private UserProfile owner;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
