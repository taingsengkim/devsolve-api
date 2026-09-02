package kh.edu.istad.ite.devsoleapi.feature.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * One result, in the shape every index shares.
 *
 * @param type     which index it came from — {@code programs}, {@code showcases},
 *                 {@code problems}, {@code organizations} or {@code users}.
 * @param slug     how the thing is addressed in a URL: a program handle, an
 *                 organization slug, a username, or the id for the two kinds
 *                 that have no name of their own. Pair it with {@code type} to
 *                 build a link.
 * @param snippet  the matching stretch of the body, cropped around the match
 *                 and with each matched word wrapped in {@code <mark>}. The
 *                 only field here that carries markup — {@code title} and
 *                 {@code subtitle} are plain, so a caller that does not want to
 *                 render HTML can use them as they are.
 * @param document the whole indexed document, so a caller that wants more than
 *                 a headline — a program's bounty range, a showcase's tags —
 *                 does not have to fetch the row to get it. The fields differ
 *                 by type.
 */
public record SearchHit(
        String type,
        String id,
        String slug,
        String title,
        String subtitle,

        @Schema(description = "Cropped body text with <mark> around each match.")
        String snippet,

        String imageUrl,
        Map<String, Object> document
) {
}
