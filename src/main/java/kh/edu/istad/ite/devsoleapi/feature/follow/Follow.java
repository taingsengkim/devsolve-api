package kh.edu.istad.ite.devsoleapi.feature.follow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "follows",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_follows_follower_followable",
                columnNames = {
                        "follower_id",
                        "followable_type",
                        "followable_id"
                }
        )
)
public class Follow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "follower_id", nullable = false)
    private UserProfile follower;

    @Enumerated(EnumType.STRING)
    @Column(name = "followable_type", nullable = false, length = 20)
    private FollowType followableType;

    @Column(name = "followable_id", nullable = false)
    private UUID followableId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
