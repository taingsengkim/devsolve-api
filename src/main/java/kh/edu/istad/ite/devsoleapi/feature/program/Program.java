package kh.edu.istad.ite.devsoleapi.feature.program;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_reward.ProgramReward;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "programs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Program extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "handle", nullable = false, unique = true, length = 100)
    private String handle;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "engagement_type",
            nullable = false,
            columnDefinition = "engagement_type_enum"
    )
    private EngagementType engagementType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "state",
            nullable = false,
            columnDefinition = "program_state_enum"
    )
    @Builder.Default
    private ProgramState state = ProgramState.DRAFT;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "submission_state",
            nullable = false,
            columnDefinition = "submission_state_enum"
    )
    @Builder.Default
    private SubmissionState submissionState = SubmissionState.PENDING_REVIEW;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "visibility",
            nullable = false,
            columnDefinition = "visibility_enum"
    )
    @Builder.Default
    private Visibility visibility = Visibility.PRIVATE;

    @Column(name = "policy", columnDefinition = "TEXT")
    private String policy;

    @Column(name = "proof_of_concept_requirements", columnDefinition = "TEXT")
    private String proofOfConceptRequirements;

    @Column(name = "offers_bounties", nullable = false)
    @Builder.Default
    private Boolean offersBounties = true;

    @Column(name = "minimum_bounty", precision = 10, scale = 2)
    private BigDecimal minimumBounty;

    @Column(name = "maximum_bounty", precision = 10, scale = 2)
    private BigDecimal maximumBounty;

    @OneToMany(
            mappedBy = "program",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<ProgramAsset> assets = new ArrayList<>();

    @OneToMany(
            mappedBy = "program",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<ProgramReward> rewards = new ArrayList<>();

    @OneToMany(
            mappedBy = "program",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<ProgramUpdate> updates = new ArrayList<>();
}
