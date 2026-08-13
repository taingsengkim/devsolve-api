package kh.edu.istad.ite.devsoleapi.feature.comments;

import jakarta.persistence.*;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentRemovalReason;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Getter
@Setter
@Entity
@Table( name = "comments")
public class Comment {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "commentable_type", nullable = false)
    private CommentableType commentableType;

    @Column(name = "commentable_id", nullable = false)
    private UUID commentableId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private Comment parentComment;

    @OneToMany(mappedBy = "parentComment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> replies = new ArrayList<>();


    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_internal", nullable = false)
    private boolean internal;

    /**
     * Set the first time the author changes the text after the grace period,
     * and never cleared. {@code updatedAt} cannot answer "was this edited?" —
     * Hibernate also bumps it when a comment is removed or moderated, and it
     * already equals {@code createdAt} on a comment nobody has touched.
     */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    /**
     * When set, the comment is a tombstone: it keeps its place in the thread
     * so replies underneath it survive, but its text is gone and readers see
     * only that something was here. The row is still live, so
     * {@code deletedAt} stays null.
     */
    @Column(name = "removed_at")
    private LocalDateTime removedAt;

    @Column(name = "removed_by")
    private UUID removedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "removal_reason", length = 20)
    private CommentRemovalReason removalReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * The comment is gone entirely and no query returns it. Reserved for
     * comments nothing hangs off — anything with live replies is tombstoned
     * via {@link #removedAt} instead, so a delete never takes somebody else's
     * writing with it.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean isRemoved() {
        return removedAt != null;
    }
}
