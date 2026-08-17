package kh.edu.istad.ite.devsoleapi.common.content;

import java.util.List;

/**
 * What a scan found, split by what the platform does about it.
 *
 * <p>Two lists rather than one list of terms carrying a severity, because
 * every caller acts on exactly one of them: the write path rejects on
 * {@link #blocked()} and never looks at the rest, and the flagger raises a
 * moderator flag from {@link #flagged()} and never looks at the rest.
 *
 * <p>The terms are the canonical spellings from the word lists, not the text
 * the author actually typed. An author who wrote "f&#42;&#42;k" produced a hit on
 * "fuck", and that is the useful thing to put in front of a moderator.
 */
public record ProfanityVerdict(List<String> blocked, List<String> flagged) {

    public ProfanityVerdict {
        blocked = List.copyOf(blocked);
        flagged = List.copyOf(flagged);
    }

    public static ProfanityVerdict clean() {
        return new ProfanityVerdict(List.of(), List.of());
    }

    public boolean isBlocked() {
        return !blocked.isEmpty();
    }

    public boolean isFlagged() {
        return !flagged.isEmpty();
    }
}
