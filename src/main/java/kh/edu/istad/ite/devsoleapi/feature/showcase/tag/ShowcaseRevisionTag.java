package kh.edu.istad.ite.devsoleapi.feature.showcase.tag;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.Tag;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseRevision;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A tag on an unpublished showcase revision. These rows never count towards
 * {@link Tag#getUsageCount()}: only tags on a published showcase do.
 */
@Entity
@Table(name = "showcase_revision_tags")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShowcaseRevisionTag {

    @EmbeddedId
    private ShowcaseRevisionTagId id;

    @MapsId("revisionId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private ShowcaseRevision revision;

    @MapsId("tagId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
