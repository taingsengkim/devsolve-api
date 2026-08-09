package kh.edu.istad.ite.devsoleapi.feature.solution;

import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.AcceptedSolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.UpdateSolutionReviewStatusRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

public interface SolutionService {

    SolutionResponse createSolution(UUID problemId, SolutionRequest request);

    Page<SolutionResponse> getSolutionsByProblemId(UUID problemId, int pageNumber, int pageSize);

    SolutionResponse getById(UUID solutionId);

    Page<SolutionResponse> getMine(int pageNumber, int pageSize);

    Page<SolutionResponse> getPublicByAuthor(UUID authorId, int pageNumber, int pageSize);

    Page<SolutionResponse> getForModeration(ReviewStatus reviewStatus, int pageNumber, int pageSize);

    SolutionResponse getAdminById(UUID solutionId);

    SolutionResponse updateSolution(UUID id, SolutionUpdateRequest request, long expectedVersion);

    void deleteSolution(UUID id);

    ProblemResponse setAcceptedSolution(UUID problemId, AcceptedSolutionRequest request);

    ProblemResponse removeAcceptedSolution(UUID problemId);

    SolutionResponse updateReviewStatus(UUID solutionId, UpdateSolutionReviewStatusRequest request);

    SolutionResponse uploadAttachment(
            UUID solutionId,
            MultipartFile file,
            long expectedVersion
    );

    void removeAttachment(
            UUID solutionId,
            UUID attachmentId,
            long expectedVersion
    );

    URI createAttachmentDownloadUrl(UUID solutionId, UUID attachmentId);
}
