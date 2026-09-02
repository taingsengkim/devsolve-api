package kh.edu.istad.ite.devsoleapi.feature.search.indexes;

import kh.edu.istad.ite.devsoleapi.feature.category.Category;
import kh.edu.istad.ite.devsoleapi.feature.category.CategoryRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.ProblemTag;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.ProblemTagRepository;
import kh.edu.istad.ite.devsoleapi.feature.search.DocumentBatch;
import kh.edu.istad.ite.devsoleapi.feature.search.IndexSettings;
import kh.edu.istad.ite.devsoleapi.feature.search.SearchDocument;
import kh.edu.istad.ite.devsoleapi.feature.search.SearchDocuments;
import kh.edu.istad.ite.devsoleapi.feature.search.SearchIndexDefinition;
import kh.edu.istad.ite.devsoleapi.feature.search.SyncCursor;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Problems that have been published, in any of the three states a reader is
 * allowed to see.
 *
 * <p>The body indexed here is the description alone, not the reproduction steps
 * or the environment rows. Those are lists of short, highly repetitive strings —
 * every problem has a "Node 20" in it somewhere — and folding them in would
 * dilute the ranking of the text that actually distinguishes one problem from
 * another.
 */
@Component
@Order(30)
@RequiredArgsConstructor
public class ProblemSearchIndex implements SearchIndexDefinition {

    public static final String NAME = "problems";

    /**
     * The same three the public feed serves. RESOLVED and CLOSED stay findable
     * on purpose: a problem someone already solved is the most useful thing a
     * search can return.
     */
    private static final Set<ProblemStatus> PUBLIC_STATUSES = EnumSet.of(
            ProblemStatus.PUBLISHED,
            ProblemStatus.RESOLVED,
            ProblemStatus.CLOSED
    );

    private final ProblemRepository problemRepository;
    private final ProblemTagRepository problemTagRepository;
    private final CategoryRepository categoryRepository;
    private final UserProfileRepository userProfileRepository;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public IndexSettings settings() {
        return new IndexSettings(
                List.of(
                        SearchDocuments.TITLE,
                        "tags",
                        "errorMessage",
                        SearchDocuments.BODY,
                        "authorName"
                ),
                List.of(
                        "authorId",
                        "categoryId",
                        "categorySlug",
                        "status",
                        "problemType",
                        "sdlcPhase",
                        "severity",
                        "tagSlugs"
                ),
                List.of(
                        SearchDocuments.UPDATED_AT,
                        SearchDocuments.CREATED_AT,
                        "publishedAt",
                        "viewCount"
                ),
                IndexSettings.rankedBy("viewCount:desc")
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentBatch loadChangedSince(SyncCursor cursor, int size) {
        Slice<Problem> problems = problemRepository.findChangedSince(
                cursor.changedAt(),
                cursor.id(),
                PageRequest.of(0, size)
        );
        if (problems.isEmpty()) {
            return DocumentBatch.empty();
        }

        Map<UUID, List<ProblemTag>> tags = problemTagRepository
                .findAllByProblemIdIn(problems.stream()
                        .map(Problem::getId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.groupingBy(
                        tag -> tag.getId().getProblemId()
                ));

        Map<UUID, Category> categories = categoryRepository
                .findAllById(problems.stream()
                        .map(Problem::getCategoryId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        Category::getId,
                        category -> category
                ));

        Map<UUID, UserProfile> authors = userProfileRepository
                .findAllById(problems.stream()
                        .map(Problem::getAuthorId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        UserProfile::getId,
                        author -> author
                ));

        List<SearchDocument> documents = problems.stream()
                .map(problem -> toDocument(
                        problem,
                        tags.getOrDefault(problem.getId(), List.of()),
                        categories.get(problem.getCategoryId()),
                        authors.get(problem.getAuthorId())
                ))
                .toList();

        return DocumentBatch.of(documents, problems.hasNext());
    }

    private SearchDocument toDocument(
            Problem problem,
            List<ProblemTag> tags,
            Category category,
            UserProfile author
    ) {
        LocalDateTime changedAt = problem.getUpdatedAt();

        if (problem.getDeletedAt() != null
                || !PUBLIC_STATUSES.contains(problem.getStatus())) {
            return SearchDocument.removed(problem.getId(), changedAt);
        }

        Map<String, Object> document = SearchDocuments.envelope(
                NAME,
                problem.getId(),
                problem.getTitle(),
                category == null ? null : category.getName(),
                problem.getDescription(),
                null,
                problem.getId().toString()
        );

        document.put(
                "authorId",
                problem.getAuthorId() == null
                        ? null
                        : problem.getAuthorId().toString()
        );
        document.put("authorName", author == null ? null : author.getFullName());
        document.put(
                "authorUsername",
                author == null ? null : author.getUsername()
        );
        document.put(
                "authorAvatarUrl",
                author == null ? null : author.getAvatarUrl()
        );
        document.put(
                "categoryId",
                category == null ? null : category.getId().toString()
        );
        document.put("categoryName", category == null ? null : category.getName());
        document.put("categorySlug", category == null ? null : category.getSlug());
        document.put("status", SearchDocuments.nameOf(problem.getStatus()));
        document.put(
                "problemType",
                SearchDocuments.nameOf(problem.getProblemType())
        );
        document.put("sdlcPhase", SearchDocuments.nameOf(problem.getSdlcPhase()));
        document.put("severity", SearchDocuments.nameOf(problem.getSeverity()));
        document.put("errorMessage", problem.getErrorMessage());
        document.put("tags", tags.stream()
                .map(tag -> tag.getTag().getName())
                .toList());
        document.put("tagSlugs", tags.stream()
                .map(tag -> tag.getTag().getSlug())
                .toList());
        document.put("viewCount", problem.getViewCount());
        document.put(
                "publishedAt",
                SearchDocuments.epochSeconds(problem.getPublishedAt())
        );
        document.put(
                SearchDocuments.CREATED_AT,
                SearchDocuments.epochSeconds(problem.getCreatedAt())
        );
        document.put(
                SearchDocuments.UPDATED_AT,
                SearchDocuments.epochSeconds(changedAt)
        );

        return SearchDocument.indexed(problem.getId(), changedAt, document);
    }
}
