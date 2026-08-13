package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.problem.dto.CreateProblemRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemModerationRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.SdlcPhase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

public interface ProblemService {

    Page<ProblemResponse> findPublished(
            UUID categoryId,
            SdlcPhase sdlcPhase,
            String tag,
            String technology,
            Pageable pageable
    );

    Page<ProblemResponse> findMine(Pageable pageable);

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
