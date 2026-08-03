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
                .sdlcPhase(request.sdlcPhase())
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
        return new ProblemResponse(
                problem.getId(),
                toAuthorSummary(author),
                toCategorySummary(category),
                problem.getTitle(),
                problem.getDescription(),
                problem.getSdlcPhase(),
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
