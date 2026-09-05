package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import java.util.UUID;

/**
 * One card in the "more like this" strip under a showcase.
 *
 * <p>Not a {@link ShowCasesResponse}: that record carries steps, tags, viewer
 * state and five counters, none of which a thumbnail renders and all of which
 * would be read six times over to fill a strip. What is here is what the card
 * draws and nothing else.
 */
public record RelatedShowcaseResponse(
        UUID id,
        String title,
        String coverImageUrl,
        String authorName,
        String categoryName,
        int viewCount
) {
}
