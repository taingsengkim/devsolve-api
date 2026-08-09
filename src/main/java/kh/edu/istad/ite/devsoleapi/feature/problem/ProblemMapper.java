package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.category.Category;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.CreateProblemRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.ProblemTag;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class ProblemMapper {

    public Problem toEntity(
            CreateProblemRequest request,
            java.util.UUID authorId
    ) {
        return Problem.builder()
                .authorId(authorId)
                .categoryId(request.categoryId())
                .title(request.title())
                .description(request.description())
                .problemType(request.problemType())
                .sdlcPhase(request.sdlcPhase())
                .severity(request.severity())
                .expectedBehavior(request.expectedBehavior())
                .actualBehavior(request.actualBehavior())
                .reproductionSteps(request.reproductionSteps() == null
                        ? new java.util.ArrayList<>()
                        : new java.util.ArrayList<>(request.reproductionSteps()))
                .environment(request.environment() == null
                        ? new java.util.ArrayList<>()
                        : request.environment().stream()
                        .map(item -> new ProblemEnvironment(
                                item.technology(),
                                item.version()
                        ))
                        .collect(java.util.stream.Collectors.toCollection(
                                java.util.ArrayList::new
                        )))
                .attemptsTried(request.attemptsTried())
                .errorMessage(request.errorMessage())
                .repositoryUrl(request.repositoryUrl())
                .build();
    }

    public ProblemResponse toResponse(
            Problem problem,
            UserProfile author,
            Category category,
            List<ProblemTechnology> technologies,
            List<ProblemTag> problemTags,
            List<ProblemAttachment> attachments,
            List<String> warnings
    ) {
        return toResponse(
                problem,
                author,
                category,
                technologies,
                problemTags,
                attachments,
                warnings,
                ProblemResponseMetrics.empty()
        );
    }

    public ProblemResponse toResponse(
            Problem problem,
            UserProfile author,
            Category category,
            List<ProblemTechnology> technologies,
            List<ProblemTag> problemTags,
            List<ProblemAttachment> attachments,
            List<String> warnings,
            ProblemResponseMetrics metrics
    ) {
        return new ProblemResponse(
                problem.getId(),
                toAuthorSummary(author),
                toCategorySummary(category),
                problem.getTitle(),
                problem.getDescription(),
                problem.getProblemType(),
                problem.getSdlcPhase(),
                problem.getSeverity(),
                problem.getExpectedBehavior(),
                problem.getActualBehavior(),
                List.copyOf(problem.getReproductionSteps()),
                problem.getEnvironment().stream()
                        .map(item -> new ProblemResponse.EnvironmentSummary(
                                item.getTechnology(),
                                item.getVersion()
                        ))
                        .toList(),
                problem.getAttemptsTried(),
                problem.getErrorMessage(),
                problem.getRepositoryUrl(),
                problem.getStatus(),
                problem.getViewCount(),
                technologies.stream()
                        .map(this::toTechnologySummary)
                        .sorted(Comparator.comparing(
                                ProblemResponse.TechnologySummary::name,
                                String.CASE_INSENSITIVE_ORDER
                        ))
                        .toList(),
                problemTags.stream()
                        .map(this::toTagSummary)
                        .sorted(Comparator.comparing(
                                ProblemResponse.TagSummary::slug
                        ))
                        .toList(),
                attachments.stream()
                        .map(this::toAttachmentSummary)
                        .toList(),
                warnings,
                metrics.solutionCount(),
                metrics.commentCount(),
                metrics.voteScore(),
                metrics.bookmarkCount(),
                problem.getAcceptedSolutions().stream()
                        .map(ProblemAcceptedSolution::getSolutionId)
                        .toList(),
                metrics.bookmarkedByViewer(),
                metrics.viewerVote(),
                metrics.canEdit(),
                metrics.canDelete(),
                metrics.canAcceptSolution(),
                problem.getPublishedAt(),
                problem.getDeletedAt(),
                problem.getVersion(),
                problem.getCreatedAt(),
                problem.getUpdatedAt()
        );
    }

    private ProblemResponse.AuthorSummary toAuthorSummary(
            UserProfile author
    ) {
        return new ProblemResponse.AuthorSummary(
                author.getId(),
                author.getFullName(),
                author.getAvatarUrl(),
                author.getReputation()
        );
    }

    private ProblemResponse.CategorySummary toCategorySummary(
            Category category
    ) {
        if (category == null) {
            return null;
        }
        return new ProblemResponse.CategorySummary(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getScope()
        );
    }

    private ProblemResponse.TechnologySummary toTechnologySummary(
            ProblemTechnology technology
    ) {
        return new ProblemResponse.TechnologySummary(
                technology.getId(),
                technology.getName(),
                technology.getVersion()
        );
    }

    private ProblemResponse.TagSummary toTagSummary(ProblemTag problemTag) {
        return new ProblemResponse.TagSummary(
                problemTag.getTag().getId(),
                problemTag.getTag().getName(),
                problemTag.getTag().getSlug()
        );
    }

    private ProblemResponse.AttachmentSummary toAttachmentSummary(
            ProblemAttachment attachment
    ) {
        return new ProblemResponse.AttachmentSummary(
                attachment.getId(),
                attachment.getOriginalFileName(),
                attachment.getMimeType(),
                attachment.getSizeBytes(),
                attachment.getUploadedBy(),
                attachment.getCreatedAt(),
                "/api/v1/problems/"
                        + attachment.getProblem().getId()
                        + "/attachments/"
                        + attachment.getId()
                        + "/download"
        );
    }
}
