package kh.edu.istad.ite.devsoleapi.feature.vote;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "votes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_votes_user_votable",
                        columnNames = {
                                "user_id",
                                "votable_type",
                                "votable_id"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vote {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "votable_type", nullable = false)
    private VoteType votableType;

    @Column(name = "votable_id", nullable = false)
    private UUID votableId;

    @Column(name = "vote_value", nullable = false)
    private Short voteValue;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}