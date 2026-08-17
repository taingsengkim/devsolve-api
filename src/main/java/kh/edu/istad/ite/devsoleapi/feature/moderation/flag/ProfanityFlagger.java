package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import kh.edu.istad.ite.devsoleapi.common.content.ContentSafetyRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Puts content that used ordinary profanity in front of a moderator without
 * standing in the author's way.
 *
 * <p>A word list cannot tell swearing at a compiler from swearing at a
 * person, and those two deserve opposite outcomes, so the filter does not
 * decide: the content is saved, the author is not interrupted, and a human
 * reads it. The slur half of the lists is handled elsewhere, on the way in,
 * where a decision can be made without a person.
 *
 * <p>Its own bean rather than a method on {@link ContentFlagService},
 * because that service already depends on the comment and program services
 * in order to act on a resolved flag, and having them depend back on it to
 * raise one would be a cycle Spring refuses to start. This holds nothing but
 * the repository, so anything can call it.
 */
@Component
@RequiredArgsConstructor
public class ProfanityFlagger {

    private final ContentFlagRepository contentFlagRepository;

    /**
     * Scans what the author wrote and raises a flag if it needs one.
     *
     * <p>Call after the row exists — a flag points at an ID. Silent when the
     * content is clean, which is almost always.
     *
     * @param parts the prose fields of the content, in whatever order; they
     *              are scanned as one document.
     */
    public void review(
            FlaggableType flaggableType,
            UUID flaggableId,
            String... parts
    ) {
        List<String> terms = ContentSafetyRules.profanity(parts);
        if (terms.isEmpty()) {
            return;
        }
        // An edit re-scans the whole thing, so without this a single comment
        // could raise a flag per save and bury the queue on its own.
        if (contentFlagRepository
                .existsBySourceAndFlaggableTypeAndFlaggableId(
                        FlagSource.AUTOMATED,
                        flaggableType,
                        flaggableId
                )) {
            return;
        }

        ContentFlag flag = new ContentFlag();
        flag.setSource(FlagSource.AUTOMATED);
        flag.setFlaggableType(flaggableType);
        flag.setFlaggableId(flaggableId);
        flag.setReason(FlagReason.OFFENSIVE);
        flag.setStatus(FlagStatus.PENDING);
        // Named here and nowhere else. The admin queue is the only reader of
        // this field -- an automated flag has no reporter, so it never
        // appears in anybody's "my reports" -- and a moderator who can see
        // which word matched can dismiss a false positive at a glance.
        flag.setDescription(
                "Matched by the content filter: " + String.join(", ", terms)
        );
        contentFlagRepository.save(flag);
    }
}
