package co.istad.ite.devsoleapi.feature.reports.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recognitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recognition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * User profile ID (Keycloak user identifier)
     */
    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "program_id", nullable = false)
    private UUID programId;

    @Column(name = "report_id")
    private UUID reportId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * User profile ID of the person who awarded recognition
     */
    @Column(name = "awarded_by", nullable = false, length = 255)
    private String awardedBy;

    @Column(name = "awarded_at", nullable = false)
    private Instant awardedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (awardedAt == null) {
            awardedAt = now;
        }

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}