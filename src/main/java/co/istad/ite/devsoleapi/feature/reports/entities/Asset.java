package co.istad.ite.devsoleapi.feature.reports.entities;

import co.istad.ite.devsoleapi.feature.reports.enums.AssetType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Asset {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "program_id")
    private UUID programId;

    @Column(name = "name")
    private String name;

    @Column(name = "identifier")
    private String identifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type")
    private AssetType assetType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
