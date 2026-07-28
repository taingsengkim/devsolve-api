package kh.edu.istad.ite.devsoleapi.feature.solution;


import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SolutionServiceImpl implements SolutionService {

    private final SolutionRepository solutionRepository;
    private final ProblemRepository problemRepository;

    @Override
    @Transactional
    public SolutionResponse createSolution(UUID problemId, SolutionRequest request) {
        Problem problem = problemRepository.findById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + problemId));

        String currentUser = AuthUtils.extractUserId();
        UUID currentUserId = UUID.fromString(currentUser);

        // Manually build the entity
        Solution solution = Solution.builder()
                .problem(problem)
                .authorId(currentUserId)
                .description(request.description())
                .videoUrl(request.videoUrl())
                .diagramUrl(request.diagramUrl())
                .reviewStatus(ReviewStatus.PENDING) // default
                .build();

        Solution saved = solutionRepository.save(solution);
        log.info("Solution created for problem {} by user {}", problemId, currentUserId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public Page<SolutionResponse> getSolutionsByProblemId(UUID problemId, Pageable pageable) {
        if (!problemRepository.existsById(problemId)) {
            throw new ResourceNotFoundException("Problem not found with id: " + problemId);
        }
        return solutionRepository.findAllByProblemIdAndDeletedAtIsNull(problemId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public SolutionResponse updateSolution(UUID id, SolutionUpdateRequest request) {
        Solution solution = solutionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found with id: " + id));

        String currentUser = AuthUtils.extractUserId();
        UUID currentUserId = UUID.fromString(currentUser);
        if (!solution.getAuthorId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the author of this solution");
        }

        if (solution.getReviewStatus() == ReviewStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Accepted solutions cannot be edited");
        }

        // Manual update of fields that are present in the request
        if (request.description() != null) {
            solution.setDescription(request.description());
        }
        if (request.videoUrl() != null) {
            solution.setVideoUrl(request.videoUrl());
        }
        if (request.diagramUrl() != null) {
            solution.setDiagramUrl(request.diagramUrl());
        }

        // Optionally reset moderation fields if you want re‑review
        // solution.setReviewStatus(ReviewStatus.PENDING);
        // solution.setReviewedAt(null);
        // solution.setReviewedBy(null);
        // solution.setRejectionReason(null);

        Solution updated = solutionRepository.save(solution);
        log.info("Solution {} updated by user {}", id, currentUserId);
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteSolution(UUID id) {
        Solution solution = solutionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found with id: " + id));

        String currentUser = AuthUtils.extractUserId();
        UUID currentUserId = UUID.fromString(currentUser);
        boolean isAuthor = solution.getAuthorId().equals(currentUserId);
        boolean isAdmin = AuthUtils.hasRole("ADMIN");
        if (!isAuthor && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to delete this solution");
        }

        solution.setDeletedAt(LocalDateTime.now());
        solutionRepository.save(solution);
        log.info("Solution {} soft-deleted by user {}", id, currentUserId);
    }

    @Override
    @Transactional
    public SolutionResponse acceptSolution(UUID id) {
        Solution solution = solutionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found with id: " + id));

        Problem problem = solution.getProblem();
        String currentUser = AuthUtils.extractUserId();
        UUID currentUserId = UUID.fromString(currentUser);
        if (!problem.getAuthorId().equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the problem author can accept a solution");
        }

        if (solution.getReviewStatus() == ReviewStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This solution is already accepted");
        }

        solution.setReviewStatus(ReviewStatus.ACCEPTED);
        // Optionally set reviewed fields
        // solution.setReviewedBy(currentUserId);
        // solution.setReviewedAt(LocalDateTime.now());

        Solution updated = solutionRepository.save(solution);
        log.info("Solution {} accepted by problem author {}", id, currentUserId);
        return toResponse(updated);
    }

    @Override
    @Transactional
    public SolutionResponse moderateSolution(UUID id, ReviewStatus status, String rejectionReason) {
        if (status != ReviewStatus.APPROVED && status != ReviewStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only APPROVED or REJECTED allowed for moderation");
        }
        Solution solution = solutionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solution not found with id: " + id));


        String currentUser = AuthUtils.extractUserId();
        UUID adminId = UUID.fromString(currentUser);


        // Admin role is already checked in controller, but could add extra check here

        solution.setReviewStatus(status);
        solution.setReviewedBy(adminId);
        solution.setReviewedAt(LocalDateTime.now());
        if (status == ReviewStatus.REJECTED) {
            solution.setRejectionReason(rejectionReason);
        } else {
            solution.setRejectionReason(null);
        }

        Solution updated = solutionRepository.save(solution);
        log.info("Solution {} moderated by admin {} to status {}", id, adminId, status);
        return toResponse(updated);
    }

    // ---------- Private mapping helper ----------
    private SolutionResponse toResponse(Solution solution) {
        return new SolutionResponse(
                solution.getId(),
                solution.getProblem().getId(),  // problemId
                solution.getAuthorId(),
                solution.getDescription(),
                solution.getVideoUrl(),
                solution.getDiagramUrl(),
                solution.getReviewStatus(),
                solution.getReviewedBy(),
                solution.getReviewedAt(),
                solution.getRejectionReason(),
                solution.getCreatedAt(),
                solution.getUpdatedAt()
        );
    }
}