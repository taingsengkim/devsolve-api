package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemDetailResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import org.springframework.stereotype.Component;

@Component
public class ProblemMapper {

    public ProblemResponse toResponse(Problem problem) {
        if (problem == null) return null;
        return new ProblemResponse(
                problem.getId(),
                problem.getAuthorId(),
                problem.getCategoryId(),
                problem.getTitle(),
                problem.getDescription(),
                problem.getStatus(),
                problem.getViewCount()
        );
    }

    public ProblemDetailResponse toDetailResponse(Problem problem) {
        if (problem == null) return null;
        return new ProblemDetailResponse(
                problem.getId(),
                problem.getAuthorId(),
                problem.getCategoryId(),
                problem.getTitle(),
                problem.getDescription(),
                problem.getStatus(),
                problem.getViewCount()
        );
    }

    public Problem toEntity(ProblemRequest request) {
        if (request == null) return null;
        return Problem.builder()
                .categoryId(request.categoryId())
                .title(request.title())
                .description(request.description())
                .build();
        // Note: id, authorId, status, viewCount, timestamps are set elsewhere
    }
}