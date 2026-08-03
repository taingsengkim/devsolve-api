package kh.edu.istad.ite.devsoleapi.feature.solution;


import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.UpdateSolutionReviewStatusRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import java.util.UUID;

public interface SolutionService {
    SolutionResponse createSolution(UUID problemId, SolutionRequest request);

    Page<SolutionResponse> getSolutionsByProblemId(
            UUID problemId,
            int pageNumber,
            int pageSize
    );

    SolutionResponse getById(UUID solutionId);

    Page<SolutionResponse> getMine(
            int pageNumber,
            int pageSize
    );

    Page<SolutionResponse> getPublicByAuthor(
            UUID authorId,
            int pageNumber,
            int pageSize
    );

    Page<SolutionResponse> getForModeration(
            ReviewStatus reviewStatus,
            int pageNumber,
            int pageSize
    );

    SolutionResponse getAdminById(UUID solutionId);

    SolutionResponse updateSolution(UUID id, SolutionUpdateRequest request);

    void deleteSolution(UUID id);

    SolutionResponse acceptSolution(UUID id);

    SolutionResponse updateReviewStatus(
            UUID solutionId,
            UpdateSolutionReviewStatusRequest request
    );
}
