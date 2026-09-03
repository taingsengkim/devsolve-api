package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.problem.dto.CreateProblemRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemModerationRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.RelatedProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import kh.edu.istad.ite.devsoleapi.common.listing.ListingSort;

public interface ProblemService {

    /**
     * @param sort when it orders by score the caller's {@code pageable} sort
     *             is ignored, because the ordering comes from the query
     */
    Page<ProblemResponse> findPublished(
            UUID categoryId,
            SdlcPhase sdlcPhase,
            String tag,
            String technology,
            String query,
            ProblemStatus status,
            boolean unansweredOnly,
            ListingSort sort,
            Pageable pageable
    );

    Page<ProblemResponse> findMine(Pageable pageable);

    /**
     * Published problems that resemble text somebody is typing into a new
     * problem, answered while they type. Solved ones rank first.
     *
     * <p>Returns empty rather than throwing when the query is too short to
     * rank on: the caller is a keystroke handler, and the first few characters
     * of a title are always too short.
     *
     * @param query     free text from the draft — the title, or whichever
     *                  field the author is editing
     * @param excludeId the problem being edited, or null for an unsaved draft
     * @param limit     clamped to a sane ceiling by the implementation
     */
    List<RelatedProblemResponse> findRelated(
            String query,
            UUID excludeId,
            int limit
    );

    Page<ProblemResponse> findPublishedByAuthor(
            UUID authorId,
            Pageable pageable
    );

    Page<ProblemResponse> findForModeration(
            ProblemStatus status,
            Pageable pageable
    );

    ProblemResponse findById(UUID id);

    ProblemResponse createDraft(CreateProblemRequest request);

    ProblemResponse createAndSubmit(CreateProblemRequest request);

    /**
     * Applies an edit to a draft, a rejected problem, or live published work.
     * Live edits stay published; they do not go back through moderation.
     */
    ProblemResponse update(
            UUID id,
            ProblemUpdateRequest request,
            long expectedVersion
    );

    ProblemResponse submit(UUID id);

    ProblemResponse moderate(
            UUID id,
            ProblemModerationRequest request
    );

    /**
     * Publishes a problem the review model cleared. Answers false rather than
     * throwing when there is nothing left to publish.
     */
    boolean autoPublish(UUID id);

    void softDelete(UUID id);

    ProblemResponse uploadAttachment(
            UUID id,
            MultipartFile file
    );

    void removeAttachment(UUID problemId, UUID attachmentId);

    URI createAttachmentDownloadUrl(
            UUID problemId,
            UUID attachmentId
    );

    void incrementViewCount(UUID id);
}
