package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import java.util.UUID;

/**
 * Who wrote a showcase, as much of it as fits on a card.
 *
 * <p>The listing's flat {@link ShowCasesSummaryResponse#authorId()} and
 * {@code authorName} carry the same person in the shape older clients already
 * read; this adds the handle a profile link needs and the face a grid of cards
 * is mostly made of.
 *
 * <p>Deliberately not {@link ShowcaseAuthorResponse}, which the detail page
 * uses: that one carries a follower count and whether the reader follows them,
 * and neither can go in a shared listing cache — the first is a query per card,
 * the second is per viewer.
 */
public record ShowcaseAuthorCard(
        UUID id,
        String username,
        String fullName,
        String avatarUrl
) {
}
