package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

import kh.edu.istad.ite.devsoleapi.feature.problem.tag.dto.TagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reads the shared tag catalogue.
 *
 * <p>Read-only on purpose: tags are created as a side effect of tagging
 * something, by {@link TagResolver}. What was missing was the other half — a
 * client sending {@code tagIds} has to be able to find out which tags exist.
 */
@Service
@RequiredArgsConstructor
public class TagService {

    static final int DEFAULT_LIMIT = 20;

    /**
     * A ceiling rather than a validation error: an autocomplete asking for a
     * thousand rows wants the top of the list, not a 400.
     */
    static final int MAX_LIMIT = 50;

    private final TagRepository tagRepository;

    /**
     * @param query optional; matched against the normalized slug, so "Node.js"
     *              and "node js" both find {@code node-js}. Null, blank, or
     *              punctuation alone lists every tag by usage.
     * @param limit optional; clamped to {@link #MAX_LIMIT}, defaulted to
     *              {@link #DEFAULT_LIMIT}
     */
    @Transactional(readOnly = true)
    public List<TagResponse> search(String query, Integer limit) {
        return tagRepository
                .search(
                        TagResolver.searchSlug(query),
                        PageRequest.of(0, clampLimit(limit))
                )
                .stream()
                .map(TagService::toResponse)
                .toList();
    }

    private static int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.clamp(limit, 1, MAX_LIMIT);
    }

    private static TagResponse toResponse(Tag tag) {
        return new TagResponse(
                tag.getId(),
                tag.getName(),
                tag.getSlug(),
                tag.getUsageCount()
        );
    }
}
