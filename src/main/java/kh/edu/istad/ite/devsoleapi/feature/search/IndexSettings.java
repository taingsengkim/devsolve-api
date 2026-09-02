package kh.edu.istad.ite.devsoleapi.feature.search;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings one index is kept at, as Meilisearch's settings endpoint wants
 * them.
 *
 * <p>Order matters in two of these and not in the others.
 * {@code searchableAttributes} is a precedence list — the {@code attribute}
 * ranking rule puts a match in the first entry above the same match in the
 * last — and {@code rankingRules} is the rule pipeline itself, applied in
 * sequence until one of them breaks the tie.
 *
 * @param searchableAttributes fields that are matched against, most important
 *                             first. Anything left out is still stored and
 *                             still returned; it just cannot be searched.
 * @param filterableAttributes fields usable in {@code filter} expressions and
 *                             as facets.
 * @param sortableAttributes   fields usable in {@code sort}. {@code updatedAt}
 *                             belongs here on every index — the synchronizer
 *                             reads the newest document to work out where it
 *                             left off.
 * @param rankingRules         the full pipeline, or an empty list to keep
 *                             Meilisearch's default one.
 */
public record IndexSettings(
        List<String> searchableAttributes,
        List<String> filterableAttributes,
        List<String> sortableAttributes,
        List<String> rankingRules
) {

    /**
     * Meilisearch's own pipeline, which every index here starts from. A
     * definition that wants a popularity tiebreaker appends to this rather
     * than replacing it: dropping {@code words} or {@code typo} would trade
     * away the reason for running a search engine in the first place.
     */
    public static final List<String> DEFAULT_RANKING_RULES = List.of(
            "words",
            "typo",
            "proximity",
            "attribute",
            "sort",
            "exactness"
    );

    public static List<String> rankedBy(String... tiebreakers) {
        return java.util.stream.Stream.concat(
                DEFAULT_RANKING_RULES.stream(),
                java.util.Arrays.stream(tiebreakers)
        ).toList();
    }

    Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("searchableAttributes", searchableAttributes);
        payload.put("filterableAttributes", filterableAttributes);
        payload.put("sortableAttributes", sortableAttributes);
        payload.put(
                "rankingRules",
                rankingRules.isEmpty() ? DEFAULT_RANKING_RULES : rankingRules
        );
        return payload;
    }
}
