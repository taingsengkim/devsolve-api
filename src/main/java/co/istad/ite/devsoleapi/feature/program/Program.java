package co.istad.ite.devsoleapi.feature.program;

import co.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import co.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import co.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import co.istad.ite.devsoleapi.feature.program.enums.Visibility;
import co.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import co.istad.ite.devsoleapi.feature.program.program_reward.ProgramReward;
import co.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdate;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "programs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    private UUID organizationId;

    @Column(unique = true, length = 100)
    private String handle;

    @Column(length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private EngagementType engagementType;

    @Enumerated(EnumType.STRING)
    private ProgramState state;  // DRAFT, ACTIVE, PAUSED, CLOSED

    @Enumerated(EnumType.STRING)
    private SubmissionState submissionState;

    @Enumerated(EnumType.STRING)
    private Visibility visibility;

    @Column(length = 3)
    private String currency; // USD, EUR, etc.

    @Column(columnDefinition = "TEXT")
    private String policy; // Markdown

    private Boolean offersBounties;


    private BigDecimal minimumBounty;
    private BigDecimal maximumBounty;


    private LocalDateTime startedAcceptingAt;

    // Relationships
    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProgramAsset> assets = new ArrayList<>();

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProgramReward> rewards = new ArrayList<>();

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProgramUpdate> updates = new ArrayList<>();

}