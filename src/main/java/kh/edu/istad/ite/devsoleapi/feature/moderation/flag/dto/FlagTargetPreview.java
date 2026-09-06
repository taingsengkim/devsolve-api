package kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagTargetStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * What was reported, enough of it to judge the report without opening it.
 *
 * <p>A flag records a type and an ID, so a queue built from flags alone shows
 * a moderator two UUIDs and asks them to guess. Fetching the content per row
 * was the obvious fix and the wrong one: twenty extra round trips a page, and
 * every one of them 404s the moment the content is taken down — so the queue
 * went blank exactly where a moderator most needs to see what they acted on.
 * This travels with the flag instead.
 *
 * <p>Always present, even when the content is gone. {@link #contentStatus} is
 * then {@link FlagTargetStatus#DELETED} and the rest is null, which the
 * frontend can say plainly. A missing object would look like a bug.
 *
 * @param title            null for a comment, which has none
 * @param snippet          the opening of the reported writing, whitespace
 *                         collapsed. Not the whole body: a queue is a place to
 *                         triage, and the full text is one click away
 * @param authorId         the organization rather than a person, for a program
 * @param contentStatus    where the content stands now, which may be nothing
 *                         like where it stood when it was reported
 * @param createdAt        when the content was written, not when it was
 *                         reported
 * @param thumbnailUrl     a showcase cover or a program logo; null otherwise
 * @param directUrl        where to read it in full, relative to the site root.
 *                         Null when there is nothing left to open
 */
public record FlagTargetPreview(
        String title,
        String snippet,
        UUID authorId,
        String authorName,
        String authorAvatarUrl,
        FlagTargetStatus contentStatus,
        LocalDateTime createdAt,
        String thumbnailUrl,
        String directUrl
) {
}
