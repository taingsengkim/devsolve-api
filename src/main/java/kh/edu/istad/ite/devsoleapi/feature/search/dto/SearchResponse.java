package kh.edu.istad.ite.devsoleapi.feature.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * The answer to one search, in either of the two modes the endpoint has.
 *
 * <p>Without a {@code type}, the search runs across every index at once and
 * {@code groups} holds a short list from each — the shape a search box wants
 * while somebody is still typing. With a {@code type}, {@code groups} holds one
 * entry and the paging fields describe it, which is the shape a results page
 * wants. One record covers both so a client that starts with the first and
 * grows into the second does not change how it reads the response.
 *
 * @param page       null in the grouped mode, where several indexes are being
 *                   sampled rather than one being paged through.
 * @param totalPages null for the same reason.
 * @param totalHits  the sum across every group.
 */
public record SearchResponse(
        String query,
        List<SearchGroup> groups,

        @Schema(nullable = true, description = "Zero-based. Null when no type was given.")
        Integer page,

        Integer size,
        long totalHits,

        @Schema(nullable = true, description = "Null when no type was given.")
        Integer totalPages
) {

    public static SearchResponse grouped(
            String query,
            List<SearchGroup> groups,
            int size
    ) {
        long total = groups.stream()
                .mapToLong(SearchGroup::totalHits)
                .sum();
        return new SearchResponse(query, groups, null, size, total, null);
    }
}
