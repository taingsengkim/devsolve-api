package kh.edu.istad.ite.devsoleapi.feature.moderation.action;


import jakarta.persistence.*;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "moderation_actions")
public class ModerationAction extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(
            name = "id",
            nullable = false,
            updatable = false
    )
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(
            name = "admin_id",
            nullable = false
    )
    private UserProfile admin;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "target_type",
            nullable = false,
            length = 20
    )
    private ModerationTargetType targetType;

    @Column(
            name = "target_id",
            nullable = false
    )
    private UUID targetId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "action",
            nullable = false,
            length = 20
    )
    private ModerationActionType action;

    @Column(
            name = "reason",
            columnDefinition = "TEXT"
    )
    private String reason;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
