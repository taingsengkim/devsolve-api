package kh.edu.istad.ite.devsoleapi.feature.solution;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.UpdateSolutionReviewStatusRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SolutionServiceImpl implements SolutionService {

    private static final List<ReviewStatus> PUBLIC_STATUSES =
            List.of(
                    ReviewStatus.APPROVED,
                    ReviewStatus.ACCEPTED
            );

    private final SolutionRepository solutionRepository;
    private final ProblemRepository problemRepository;

    @Override
    @Transactional
    public SolutionResponse createSolution(
            UUID problemId,
            SolutionRequest request
    ) {
        Problem problem = problemRepository
                .findPublicById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem not found with id: " + problemId
                ));

        if (problem.getStatus() != ProblemStatus.PUBLISHED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Solutions can only be submitted to published problems."
            );
        }

        UUID currentUserId = extractCurrentUserId();
        Solution solution = Solution.builder()
                .problem(problem)
                .authorId(currentUserId)
                .description(request.description().trim())
                .videoUrl(request.videoUrl())
                .diagramUrl(request.diagramUrl())
                .reviewStatus(ReviewStatus.PENDING)
                .build();

        Solution saved = solutionRepository.save(solution);
        log.info(
                "Solution created for problem {} by user {}",
                problemId,
                currentUserId
        );
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SolutionResponse> getSolutionsByProblemId(
            UUID problemId,
            int pageNumber,
            int pageSize
    ) {
        validatePagination(pageNumber, pageSize);
        problemRepository.findPublicById(problemId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Problem not found with id: " + problemId
                ));

        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.ASC, "createdAt")
        );

        return solutionRepository
                .findAllByProblem_IdAndReviewStatusInAndDeletedAtIsNull(
                        problemId,
                        PUBLIC_STATUSES,
                        pageable
                )
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SolutionResponse getById(UUID solutionId) {
        Solution solution = solutionRepository
                .findByIdAndReviewStatusInAndDeletedAtIsNull(
                        solutionId,
                        PUBLIC_STATUSES
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Solution not found with id: " + solutionId
                ));

        return toResponse(solution);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SolutionResponse> getMine(
            int pageNumber,
            int pageSize
    ) {
        validatePagination(pageNumber, pageSize);
        UUID currentUserId = extractCurrentUserId();
        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.DESC, "updatedAt")
        );

        return solutionRepository
                .findAllByAuthorIdAndDeletedAtIsNull(
                        currentUserId,
                        pageable
                )
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SolutionResponse> getForModeration(
            ReviewStatus reviewStatus,
            int pageNumber,
            int pageSize
    ) {
        requireAdmin();
        validatePagination(pageNumber, pageSize);
        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.ASC, "createdAt")
        );

        return solutionRepository
                .findForModeration(reviewStatus, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public SolutionResponse getAdminById(UUID solutionId) {
        requireAdmin();
        return toResponse(findActiveSolution(solutionId));
    }

    @Override
    @Transactional
    public SolutionResponse updateSolution(
            UUID id,
            SolutionUpdateRequest request
    ) {
        Solution solution = findActiveSolution(id);
        UUID currentUserId = extractCurrentUserId();
        requireAuthor(solution, currentUserId);

        if (solution.getReviewStatus() == ReviewStatus.ACCEPTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Accepted solutions cannot be edited."
            );
        }

        validateUpdateRequest(request);
        applyUpdate(solution, request);
        solution.setReviewStatus(ReviewStatus.PENDING);
        solution.setReviewedAt(null);
        solution.setReviewedBy(null);
        solution.setRejectionReason(null);

        Solution updated = solutionRepository.save(solution);
        log.info("Solution {} updated by user {}", id, currentUserId);
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteSolution(UUID id) {
        Solution solution = findActiveSolution(id);
        UUID currentUserId = extractCurrentUserId();
        boolean isAuthor = solution.getAuthorId().equals(currentUserId);
        boolean isAdmin = AuthUtils.hasRole("ADMIN");

        if (!isAuthor && !isAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not allowed to delete this solution."
            );
        }

        if (solution.getReviewStatus() == ReviewStatus.ACCEPTED
                && !isAdmin) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Accepted solutions can only be removed by an admin."
            );
        }

        if (solution.getReviewStatus() == ReviewStatus.ACCEPTED
                && solution.getProblem().getStatus()
                        == ProblemStatus.RESOLVED) {
            boolean anotherAcceptedSolutionExists =
                    solutionRepository
                            .existsByProblem_IdAndReviewStatusAndDeletedAtIsNullAndIdNot(
                                    solution.getProblem().getId(),
                                    ReviewStatus.ACCEPTED,
                                    solution.getId()
                            );

            if (!anotherAcceptedSolutionExists) {
                solution.getProblem().setStatus(
                        ProblemStatus.PUBLISHED
                );
                problemRepository.save(solution.getProblem());
            }
        }

        solution.setDeletedAt(LocalDateTime.now());
        solutionRepository.save(solution);
        log.info("Solution {} soft-deleted by user {}", id, currentUserId);
    }

    @Override
    @Transactional
    public SolutionResponse acceptSolution(UUID id) {
        Solution solution = findActiveSolution(id);
        Problem problem = solution.getProblem();
        UUID currentUserId = extractCurrentUserId();

        if (!problem.getAuthorId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the problem author can accept a solution."
            );
        }

        if (solution.getReviewStatus() != ReviewStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only an approved solution can be accepted."
            );
        }

        solution.setReviewStatus(ReviewStatus.ACCEPTED);
        problem.setStatus(ProblemStatus.RESOLVED);

        problemRepository.save(problem);
        Solution updated = solutionRepository.save(solution);
        log.info(
                "Solution {} accepted by problem author {}",
                id,
                currentUserId
        );
        return toResponse(updated);
    }

    @Override
    @Transactional
    public SolutionResponse updateReviewStatus(
            UUID solutionId,
            UpdateSolutionReviewStatusRequest request
    ) {
        requireAdmin();
        validateReviewRequest(request);

        Solution solution = findActiveSolution(solutionId);
        if (solution.getReviewStatus() != ReviewStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only pending solutions can be reviewed."
            );
        }

        UUID reviewerId = extractCurrentUserId();
        solution.setReviewStatus(request.reviewStatus());
        solution.setReviewedBy(reviewerId);
        solution.setReviewedAt(LocalDateTime.now());
        solution.setRejectionReason(
                request.reviewStatus() == ReviewStatus.REJECTED
                        ? request.rejectionReason().trim()
                        : null
        );

        Solution updated = solutionRepository.save(solution);
        log.info(
                "Solution {} reviewed by admin {} as {}",
                solutionId,
                reviewerId,
                request.reviewStatus()
        );
        return toResponse(updated);
    }

    private Solution findActiveSolution(UUID id) {
        return solutionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Solution not found with id: " + id
                ));
    }

    private void requireAuthor(
            Solution solution,
            UUID currentUserId
    ) {
        if (!solution.getAuthorId().equals(currentUserId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not the author of this solution."
            );
        }
    }

    private void applyUpdate(
            Solution solution,
            SolutionUpdateRequest request
    ) {
        if (request.description() != null) {
            solution.setDescription(request.description().trim());
        }
        if (request.videoUrl() != null) {
            solution.setVideoUrl(request.videoUrl());
        }
        if (request.diagramUrl() != null) {
            solution.setDiagramUrl(request.diagramUrl());
        }
    }

    private void validateUpdateRequest(
            SolutionUpdateRequest request
    ) {
        if (request.description() == null
                && request.videoUrl() == null
                && request.diagramUrl() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one solution field must be provided."
            );
        }
        if (request.description() != null
                && request.description().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Description must not be blank."
            );
        }
    }

    private void validateReviewRequest(
            UpdateSolutionReviewStatusRequest request
    ) {
        if (request.reviewStatus() != ReviewStatus.APPROVED
                && request.reviewStatus() != ReviewStatus.REJECTED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Review status must be APPROVED or REJECTED."
            );
        }

        boolean hasReason = request.rejectionReason() != null
                && !request.rejectionReason().isBlank();
        if (request.reviewStatus() == ReviewStatus.REJECTED
                && !hasReason) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection reason is required when rejecting a solution."
            );
        }
        if (request.reviewStatus() == ReviewStatus.APPROVED
                && hasReason) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rejection reason is only allowed for rejected solutions."
            );
        }
    }

    private void requireAdmin() {
        if (!AuthUtils.hasRole("ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only ADMIN can review solutions."
            );
        }
    }

    private UUID extractCurrentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }

    private void validatePagination(
            int pageNumber,
            int pageSize
    ) {
        if (pageNumber < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page number must be greater than or equal to 0."
            );
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and 100."
            );
        }
    }

    private SolutionResponse toResponse(Solution solution) {
        return new SolutionResponse(
                solution.getId(),
                solution.getProblem().getId(),
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
