package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What the automatic check decided about one post, kept so the author can be
 * shown it.
 *
 * <p>Until this existed the decision lived in two places that the author could
 * not read: a log line written for an operator, and a notification that is gone
 * the moment it is dismissed. A post sitting at "pending" therefore looked
 * identical whether the check had held it for a reason the author could fix or
 * whether nobody had looked yet. This row is what the pending post can point at
 * to answer that.
 *
 * <p>One row per post, not one per check. A submission is re-checked whenever
 * its author edits it, and the question the row answers is "why is this post
 * where it is now" — an audit trail of superseded verdicts answers a different
 * question, and the moderation history tables already answer that one.
 */
@Entity
@Table(
        name = "content_auto_reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_content_auto_reviews_target_content",
                columnNames = {"target", "content_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ContentAutoReview {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 20)
    private AutoApprovalTarget target;

    @Column(name = "content_id", nullable = false)
    private UUID contentId;

    /**
     * Who to show this to. Null when the submitting service could not resolve
     * an author, which the read side treats as a row nobody may see rather than
     * as an error.
     */
    @Column(name = "author_id")
    private UUID authorId;

    /**
     * The title as it stood when the check ran, so a list of an author's
     * pending posts reads on its own without joining three content tables that
     * have nothing else in common.
     */
    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "approved", nullable = false)
    private boolean approved;

    /** Null on an approval. */
    @Enumerated(EnumType.STRING)
    @Column(name = "hold", length = 20)
    private AutoApprovalHold hold;

    /**
     * The check's own sentence about this submission.
     *
     * <p>Written by the model, and shown to the author — which is a change from
     * where this started. See {@link AutoApprovalHoldNotifier} for why.
     */
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt;
}
