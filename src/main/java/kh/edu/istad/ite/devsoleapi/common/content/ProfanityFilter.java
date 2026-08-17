package kh.edu.istad.ite.devsoleapi.common.content;

import java.util.regex.Pattern;

/**
 * The platform's word lists, and the decision about which parts of a piece
 * of writing they get to see.
 *
 * <p>Everything here is written in markdown by developers, which means most
 * submissions carry a stack trace, a payload or a repository URL. None of
 * that was composed by the author and none of it should be read as if it
 * were: a base64 blob will eventually spell something, a variable can be
 * called anything, and a filter that rejects a bug report because of a hex
 * digest is worse than no filter. So code and links are cut out before the
 * dictionary is applied, and only prose is scanned.
 *
 * <p>Static, like {@link ContentSafetyRules}, and for the same reason — two
 * features must not be able to disagree about what is allowed. The word
 * lists are read once, on first use; a missing one fails loudly at startup
 * rather than quietly passing everything.
 */
public final class ProfanityFilter {

    private static final ProfanityDictionary DICTIONARY =
            ProfanityDictionary.load(
                    "/content/profanity-blocked.txt",
                    "/content/profanity-flagged.txt"
            );

    /**
     * Fenced blocks first, so that a stray backtick inside one cannot end an
     * inline span early and leave half a block in the prose.
     */
    private static final Pattern FENCED_CODE =
            Pattern.compile("(?s)```.*?```|~~~.*?~~~");

    private static final Pattern INLINE_CODE =
            Pattern.compile("`[^`\\n]*`");

    private static final Pattern LINK =
            Pattern.compile("(?i)(?:https?://|www\\.)\\S+");

    private ProfanityFilter() {
    }

    public static ProfanityVerdict scan(String... parts) {
        StringBuilder prose = new StringBuilder();
        for (String part : parts) {
            if (part != null) {
                prose.append(prose(part)).append('\n');
            }
        }
        return DICTIONARY.scan(prose.toString());
    }

    /**
     * The parts of a markdown document its author wrote in their own words.
     *
     * <p>Cuts are replaced with a space rather than removed, so that the
     * words either side of a removed span do not run together into something
     * that was never written.
     *
     * <p>An unterminated fence is left alone and therefore scanned. Treating
     * a lone "```" as opening a block that runs to the end of the document
     * would hand every author a one-line way to switch the filter off.
     */
    static String prose(String markdown) {
        String stripped = FENCED_CODE.matcher(markdown).replaceAll(" ");
        stripped = INLINE_CODE.matcher(stripped).replaceAll(" ");
        return LINK.matcher(stripped).replaceAll(" ");
    }
}
