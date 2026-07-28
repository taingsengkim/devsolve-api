package kh.edu.istad.ite.devsoleapi.feature.solution;


import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionUpdateRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.enums.ReviewStatus;
import org.springframework.stereotype.Component;

@Component
public class SolutionMapper {

    // Convert request -> entity (for creation)
    public Solution toEntity(SolutionRequest request) {
        if (request == null) return null;

        return Solution.builder()
                .description(request.description())
                .videoUrl(request.videoUrl())
                .diagramUrl(request.diagramUrl())
                .reviewStatus(ReviewStatus.PENDING) // default
                // Note: problem, authorId, id, audit fields are set later in service
                .build();
    }

    // Convert update request -> entity (used to copy changes)
    public void updateEntity(Solution target, SolutionUpdateRequest source) {
        if (source == null) return;

        if (source.description() != null) {
            target.setDescription(source.description());
        }
        if (source.videoUrl() != null) {
            target.setVideoUrl(source.videoUrl());
        }
        if (source.diagramUrl() != null) {
            target.setDiagramUrl(source.diagramUrl());
        }
    }

    // Convert entity -> response DTO
    public SolutionResponse toResponse(Solution solution) {
        if (solution == null) return null;

        return new SolutionResponse(
                solution.getId(),
                solution.getProblem() != null ? solution.getProblem().getId() : null,
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