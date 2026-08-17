package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;


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
@Table(name = "content_flags")
public class ContentFlag extends BasedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Null when {@link #source} is {@code AUTOMATED}. The alternative was a
     * system account holding the other end of the foreign key, which would
     * have put a row in user_profiles that every user search, follower count
     * and leaderboard then has to remember to exclude. An absent reporter is
     * the honest reading: nobody reported this.
     */
    @ManyToOne
    @JoinColumn(name = "reporter_id")
    private UserProfile reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private FlagSource source = FlagSource.USER;

    @Enumerated(EnumType.STRING)
    @Column(name = "flaggable_type", nullable = false, length = 20)
    private FlaggableType flaggableType;

    @Column(
            name = "flaggable_id",
            nullable = false
    )
    private UUID flaggableId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "reason",
            nullable = false
    )
    private FlagReason reason;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false
    )
    private FlagStatus status = FlagStatus.PENDING;

    @ManyToOne
    @JoinColumn(name = "reviewed_by")
    private UserProfile reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "resolution_note", columnDefinition = "TEXT")
    private String resolutionNote;
}
