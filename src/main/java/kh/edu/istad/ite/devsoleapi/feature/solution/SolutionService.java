package kh.edu.istad.ite.devsoleapi.feature.solution;


import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface SolutionService {
    SolutionResponse createSolution(UUID problemId, SolutionRequest request);
    Page<SolutionResponse> getSolutionsByProblemId(UUID problemId, Pageable pageable);
    SolutionResponse updateSolution(UUID id, SolutionUpdateRequest request);
    void deleteSolution(UUID id);
    SolutionResponse acceptSolution(UUID id);
    SolutionResponse moderateSolution(UUID id, ReviewStatus status, String rejectionReason);
}