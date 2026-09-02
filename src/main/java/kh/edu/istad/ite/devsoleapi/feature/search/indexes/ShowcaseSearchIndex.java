package kh.edu.istad.ite.devsoleapi.feature.search.indexes;

import kh.edu.istad.ite.devsoleapi.feature.category.Category;
import kh.edu.istad.ite.devsoleapi.feature.search.DocumentBatch;
import kh.edu.istad.ite.devsoleapi.feature.search.IndexSettings;
import kh.edu.istad.ite.devsoleapi.feature.search.SearchDocument;
import kh.edu.istad.ite.devsoleapi.feature.search.SearchDocuments;
import kh.edu.istad.ite.devsoleapi.feature.search.SearchIndexDefinition;
import kh.edu.istad.ite.devsoleapi.feature.search.SyncCursor;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCases;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseTag;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseTagRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Approved showcases.
 *
 * <p>Tags are indexed as text as well as as a filter. A researcher typing
 * "kubernetes" into a search box means the same thing whether the word is in
 * the title, in the write-up or on a tag, and an index that only accepted it as
 * {@code tagSlugs = kubernetes} would need the caller to know which.
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class ShowcaseSearchIndex implements SearchIndexDefinition {

    public static final String NAME = "showcases";

    private final ShowCasesRepository showCasesRepository;
    private final ShowcaseTagRepository showcaseTagRepository;

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
                        SearchDocuments.SUBTITLE,
                        "categoryName",
                        SearchDocuments.BODY
                ),
                List.of(
                        "authorId",
                        "authorUsername",
                        "categoryId",
                        "categorySlug",
                        "tagSlugs"
                ),
                List.of(
                        SearchDocuments.UPDATED_AT,
                        SearchDocuments.CREATED_AT,
                        "viewCount"
                ),
                IndexSettings.rankedBy("viewCount:desc")
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentBatch loadChangedSince(SyncCursor cursor, int size) {
        Slice<ShowCases> showcases = showCasesRepository.findChangedSince(
                cursor.changedAt(),
                cursor.id(),
                PageRequest.of(0, size)
        );
        if (showcases.isEmpty()) {
            return DocumentBatch.empty();
        }

        // Keyed off the embedded id rather than getShowcase().getId(), which
        // would initialize a lazy proxy per row for something already to hand.
        Map<UUID, List<ShowcaseTag>> tags = showcaseTagRepository
                .findAllByShowcaseIdIn(showcases.stream()
                        .map(ShowCases::getId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.groupingBy(
                        tag -> tag.getId().getShowcaseId()
                ));

        List<SearchDocument> documents = showcases.stream()
                .map(showcase -> toDocument(
                        showcase,
                        tags.getOrDefault(showcase.getId(), List.of())
                ))
                .toList();

        return DocumentBatch.of(documents, showcases.hasNext());
    }

    private SearchDocument toDocument(ShowCases showcase, List<ShowcaseTag> tags) {
        LocalDateTime changedAt = showcase.getUpdatedAt();

        if (showcase.getDeletedAt() != null
                || showcase.getReviewStatus() != ReviewStatus.APPROVED) {
            return SearchDocument.removed(showcase.getId(), changedAt);
        }

        UserProfile author = showcase.getAuthor();
        Category category = showcase.getCategory();

        Map<String, Object> document = SearchDocuments.envelope(
                NAME,
                showcase.getId(),
                showcase.getTitle(),
                author == null ? null : author.getFullName(),
                showcase.getOverview(),
                showcase.getCoverImageUrl(),
                showcase.getId().toString()
        );

        document.put(
                "authorId",
                author == null ? null : author.getId().toString()
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
        document.put("tags", tags.stream()
                .map(tag -> tag.getTag().getName())
                .toList());
        document.put("tagSlugs", tags.stream()
                .map(tag -> tag.getTag().getSlug())
                .toList());
        document.put(
                "viewCount",
                showcase.getViewCount() == null ? 0 : showcase.getViewCount()
        );
        document.put(
                SearchDocuments.CREATED_AT,
                SearchDocuments.epochSeconds(showcase.getCreatedAt())
        );
        document.put(
                SearchDocuments.UPDATED_AT,
                SearchDocuments.epochSeconds(changedAt)
        );

        return SearchDocument.indexed(showcase.getId(), changedAt, document);
    }
}
