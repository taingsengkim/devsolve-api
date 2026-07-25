package co.istad.ite.devsoleapi.feature.program.program_asset;



import co.istad.ite.devsoleapi.feature.program.Program;
import co.istad.ite.devsoleapi.feature.program.enums.AssetType;
import co.istad.ite.devsoleapi.feature.program.enums.Severity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "program_assets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProgramAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    @JsonIgnore
    private Program program;

    @Enumerated(EnumType.STRING)
    private AssetType assetType;

    private String identifier;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Boolean isInScope;

    @Enumerated(EnumType.STRING)
    private Severity maxSeverity;


}