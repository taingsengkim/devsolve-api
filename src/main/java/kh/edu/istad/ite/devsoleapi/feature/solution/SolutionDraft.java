package kh.edu.istad.ite.devsoleapi.feature.solution;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResourceRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.TestedWithRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.VerificationStepRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ApproachType;
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
 * An answer somebody has started writing and not posted.
 *
 * <p>A separate table rather than a state on the solution, for the reason
 * reports and showcases have one: summary, body and approach are all required
 * on a real solution — the body has a 30-character minimum — and a draft has
 * none of that after the first keystroke. It also keeps unposted answers out of
 * every query that counts solutions on a problem, which is most of them.
 *
 * <p>The nested lists are jsonb holding the request shapes themselves, the way
 * {@code Program.proofOfConceptRequirements} already does. They are only ever
 * read back whole, with the draft, and modelling three child tables for rows
 * that may never become a solution buys nothing.
 *
 * <p>Nothing here is validated beyond a length cap. The rules live at submit,
 * where the draft becomes a real {@code SolutionRequest} and goes through the
 * same validated path a direct post takes.
 */
@Entity
@Table(name = "solution_drafts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolutionDraft extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserProfile author;

    /**
     * Which problem this answers. A plain identifier rather than a foreign key:
     * a draft can outlive the problem being closed or soft-deleted, and a
     * constraint here would delete somebody's unfinished answer with it. Submit
     * resolves it through the normal lookup and refuses if it has stopped being
     * answerable.
     *
     * <p>Fixed at creation all the same — an answer only means anything against
     * the question it was written for.
     */
    @Column(name = "problem_id", nullable = false)
    private UUID problemId;

    @Column(name = "summary", length = 250)
    private String summary;

    @Column(name = "body_markdown", columnDefinition = "TEXT")
    private String bodyMarkdown;

    /**
     * Nullable here where a solution requires it: somebody who has written the
     * answer but not yet said which kind of fix it is still has a draft worth
     * keeping.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "approach_type", length = 20)
    private ApproachType approachType;

    @Column(name = "tradeoffs", columnDefinition = "TEXT")
    private String tradeoffs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verification_steps", columnDefinition = "jsonb")
    private List<VerificationStepRequest> verificationSteps;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tested_with", columnDefinition = "jsonb")
    private List<TestedWithRequest> testedWith;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resources", columnDefinition = "jsonb")
    private List<SolutionResourceRequest> resources;
}
