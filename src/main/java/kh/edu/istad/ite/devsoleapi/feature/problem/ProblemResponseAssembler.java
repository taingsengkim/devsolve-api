package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.category.Category;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.ProblemTag;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.ProblemTagRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the half of a problem response that reads the same for everyone.
 *
 * <p>Split out of the service so the cache can build exactly this and nothing
 * more. What is deliberately absent is {@link ProblemResponseMetrics}: the
 * counts, the viewer's vote and bookmark, and the three permissions. Every
 * response leaves here with {@link ProblemResponseMetrics#empty()} in those
 * nine components, and {@code ProblemResponses.withMetrics} fills them in for
 * whoever is asking.
 */
@Component
@RequiredArgsConstructor
class ProblemResponseAssembler {

    private final UserProfileRepository userProfileRepository;
    private final CategoryRepository categoryRepository;
    private final ProblemTechnologyRepository technologyRepository;
    private final ProblemTagRepository problemTagRepository;
    private final ProblemAttachmentRepository attachmentRepository;
    private final ProblemMapper problemMapper;
    private final ProblemContentSafety contentSafety;

    /**
     * Everything a page of problems needs from other tables, fetched once for
     * the whole page. Reading these per problem instead turns a single listing
     * into upwards of a hundred queries.
     */
    Associations load(List<Problem> problems) {
        if (problems.isEmpty()) {
            return Associations.empty();
        }
        List<UUID> problemIds = problems.stream()
                .map(Problem::getId)
                .toList();
        Set<UUID> authorIds = problems.stream()
                .map(Problem::getAuthorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> categoryIds = problems.stream()
                .map(Problem::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return new Associations(
                userProfileRepository.findAllById(authorIds).stream()
                        .collect(Collectors.toMap(
                                UserProfile::getId,
                                Function.identity()
                        )),
                categoryRepository.findAllById(categoryIds).stream()
                        .collect(Collectors.toMap(
                                Category::getId,
                                Function.identity()
                        )),
                technologyRepository
                        .findAllByProblemIdInOrderByNameAsc(problemIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                technology -> technology.getProblem().getId()
                        )),
                problemTagRepository.findAllByProblemIdIn(problemIds).stream()
                        .collect(Collectors.groupingBy(
                                problemTag -> problemTag.getProblem().getId()
                        )),
                attachmentRepository
                        .findAllByProblemIdInOrderByCreatedAtAsc(problemIds)
                        .stream()
                        .collect(Collectors.groupingBy(
                                attachment -> attachment.getProblem().getId()
                        ))
        );
    }

    ProblemResponse toResponse(
            Problem problem,
            Associations associations
    ) {
        return problemMapper.toResponse(
                problem,
                // Null rather than a 404: one problem whose author profile has
                // gone missing should cost that row its byline, not take the
                // whole page down with it.
                associations.authors().get(problem.getAuthorId()),
                associations.categories().get(problem.getCategoryId()),
                associations.technologies().getOrDefault(
                        problem.getId(),
                        List.of()
                ),
                associations.tags().getOrDefault(problem.getId(), List.of()),
                associations.attachments().getOrDefault(
                        problem.getId(),
                        List.of()
                ),
                contentSafety.warnings(
                        problem.getTitle(),
                        problem.getDescription()
                ),
                ProblemResponseMetrics.empty()
        );
    }

    /** Convenience for the single-problem paths. */
    ProblemResponse toResponse(Problem problem) {
        return toResponse(problem, load(List.of(problem)));
    }

    record Associations(
            Map<UUID, UserProfile> authors,
            Map<UUID, Category> categories,
            Map<UUID, List<ProblemTechnology>> technologies,
            Map<UUID, List<ProblemTag>> tags,
            Map<UUID, List<ProblemAttachment>> attachments
    ) {
        static Associations empty() {
            return new Associations(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of()
            );
        }
    }
}
