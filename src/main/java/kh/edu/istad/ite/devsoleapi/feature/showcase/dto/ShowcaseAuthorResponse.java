package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import java.util.UUID;

/**
 * The author card beside a showcase.
 *
 * <p>{@link ShowCasesResponse#authorId()} and {@code authorName} carry the same
 * person in the flat shape older clients already read; this record is what a
 * detail page needs to render them as somebody worth following — a face, a
 * line about themselves, and the two numbers that say whether their work is
 * worth following.
 *
 * @param publishedShowcaseCount approved, undeleted showcases only, so the
 *                               number matches what a visitor can actually open
 *                               from the profile
 * @param followedByViewer       false for a signed-out reader, who follows
 *                               nobody
 */
public record ShowcaseAuthorResponse(
        UUID id,
        String username,
        String fullName,
        String avatarUrl,
        String biography,
        int reputation,
        long publishedShowcaseCount,
        long followerCount,
        boolean followedByViewer
) {
}
