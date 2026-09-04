package kh.edu.istad.ite.devsoleapi.feature.showcase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * A showcase somebody has started writing and not posted.
 *
 * <p>A separate table rather than a DRAFT value on {@link ShowCases}, for the
 * same reason reports have one: title and overview are NOT NULL on a showcase
 * and a half-written draft has neither. Carrying drafts on the showcases table
 * would mean relaxing those columns for every real showcase and then auditing
 * every listing, feed, search index and moderation query to exclude a state
 * they were never written to expect — where anything missed puts an unfinished
 * post in front of the public.
 *
 * <p>It also fixes something specific to showcases. Creating one used to *be*
 * submitting it, so the auto-approval check saw every showcase at its thinnest
 * possible moment: title and overview only, because steps are written
 * afterwards. That is why so many were held as "too short and vague to judge".
 * A draft lets the whole write-up exist before anything reaches a reviewer.
 *
 * <p>Nothing here is validated beyond a length cap. Autosave has to accept a
 * form mid-keystroke, so the rules live at submit, where the draft becomes a
 * real {@code CreateShowCasesRequest} and goes through the same validated path
 * a direct post takes.
 */
@Entity
@Table(name = "showcase_drafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowcaseDraft extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserProfile author;

    /**
     * A plain identifier rather than a foreign key, unlike the showcase it will
     * become. A draft can outlive the category being renamed or removed, and a
     * constraint here would either block that or delete somebody's unfinished
     * work with it. Nothing reads it until submit, which resolves it through
     * the normal lookup and returns a real error if it has stopped being valid.
     */
    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "overview", columnDefinition = "TEXT")
    private String overview;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "live_url", length = 500)
    private String liveUrl;

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    /**
     * Stored as jsonb rather than join tables: they are only ever read back
     * whole, with the draft, and a draft's tags are not referenced by anything
     * else until it is posted.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tag_ids", columnDefinition = "jsonb")
    private List<UUID> tagIds;

    /** Tag names the author typed that do not exist in the catalog yet. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;
}
