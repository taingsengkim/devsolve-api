package kh.edu.istad.ite.devsoleapi.common.content;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A set of terms and the matching that finds them in text a person typed.
 *
 * <p>Naive containment fails this job in both directions at once. It misses
 * everything an author does to get past it — padding letters, swapping in
 * digits, pasting a Cyrillic lookalike — and it fires on the inside of
 * innocent words, which on a platform whose vocabulary includes "assert",
 * "class" and "analysis" is the more expensive failure of the two. So the
 * text is folded to a canonical form first, and terms are then matched with
 * guards that refuse to sit inside a longer word.
 *
 * <p>Held as an object with its terms passed in, rather than static rules
 * reading the word lists directly, so a test can state its own vocabulary
 * and assert on the matching instead of on whatever the shipped lists happen
 * to contain today.
 */
final class ProfanityDictionary {

    /**
     * A run of at least four single characters held apart by punctuation or
     * spaces — "n i g g e r", "f.a.g.g.o.t".
     *
     * <p>The scan for those works by deleting the separators, which makes
     * whatever is on either side touch, so it is pointed at runs that are
     * already spelled out one letter at a time rather than at the text as a
     * whole. Compacting everything would let two ordinary words assemble a
     * term between them — "sure tardy" reads as a slur once the space is
     * gone — and on the blocked list that costs somebody their write.
     *
     * <p>Nobody spaces out four letters by accident, so a run that matches
     * this is deliberate, whatever it turns out to spell.
     */
    private static final Pattern SPACED_RUN = Pattern.compile(
            "(?:[\\p{L}\\p{M}\\p{N}][^\\p{L}\\p{M}\\p{N}]+){3,}"
                    + "[\\p{L}\\p{M}\\p{N}]"
    );

    /**
     * Characters that carry no meaning where they appear but do break a term
     * apart if a matcher takes them literally. Latin combining marks are in
     * the list; Khmer's are deliberately not, because a Khmer word is built
     * out of them and stripping them would leave nothing to match.
     */
    private static boolean isIgnorable(int codePoint) {
        return codePoint == 0x00AD
                || codePoint == 0x200B
                || codePoint == 0x200C
                || codePoint == 0x200D
                || codePoint == 0x2060
                || codePoint == 0xFEFF
                || (codePoint >= 0x0300 && codePoint <= 0x036F);
    }

    /**
     * Letters from other alphabets that render as Latin ones. Folded
     * unconditionally: a word spelled entirely in Cyrillic lookalikes has no
     * Latin letter to key off, so there is nothing to make the decision on
     * later.
     */
    private static final Map<Integer, Integer> HOMOGLYPHS = Map.ofEntries(
            Map.entry(0x0430, (int) 'a'),
            Map.entry(0x0435, (int) 'e'),
            Map.entry(0x043E, (int) 'o'),
            Map.entry(0x0440, (int) 'p'),
            Map.entry(0x0441, (int) 'c'),
            Map.entry(0x0443, (int) 'y'),
            Map.entry(0x0445, (int) 'x'),
            Map.entry(0x0455, (int) 's'),
            Map.entry(0x0456, (int) 'i'),
            Map.entry(0x03B1, (int) 'a'),
            Map.entry(0x03B5, (int) 'e'),
            Map.entry(0x03BF, (int) 'o'),
            Map.entry(0x03C1, (int) 'p')
    );

    /**
     * Digits and symbols standing in for letters. Unlike the homoglyphs
     * these are folded only inside a token that already holds a Latin
     * letter, because this platform is full of text where a digit is a
     * digit: fold unconditionally and CVE-2021-455 reads as a slur.
     */
    private static final Map<Integer, Integer> LEET = Map.ofEntries(
            Map.entry((int) '4', (int) 'a'),
            Map.entry((int) '@', (int) 'a'),
            Map.entry((int) '3', (int) 'e'),
            Map.entry((int) '1', (int) 'i'),
            Map.entry((int) '!', (int) 'i'),
            Map.entry((int) '|', (int) 'i'),
            Map.entry((int) '0', (int) 'o'),
            Map.entry((int) '5', (int) 's'),
            Map.entry((int) '$', (int) 's'),
            Map.entry((int) '7', (int) 't')
    );

    private static final Pattern TOKEN =
            Pattern.compile("[\\p{L}\\p{M}\\p{N}@$!|]+");

    private static final Pattern ASCII_LETTER = Pattern.compile("[a-z]");

    /**
     * Suffixes a term is allowed to carry. Without these the lists would
     * need an entry per inflection, and the one everybody forgets is the one
     * that gets used.
     */
    private static final String SUFFIXES = "(?:e?s|ed|ing|er|ers|y)?";

    /**
     * Left guard is stricter than the right. Nothing alphanumeric may sit
     * before a match, which is what keeps "class" and a hex digest ending in
     * "...beefass" clean; only letters are barred after it, so "fuck2" and
     * "shit!!!" still land.
     */
    private static final String LEFT_GUARD = "(?<![a-z0-9])";
    private static final String RIGHT_GUARD = "(?![a-z])";

    private final Map<String, Pattern> blocked;
    private final Map<String, Pattern> flagged;
    private final Pattern anyBlocked;
    private final Pattern anyFlagged;

    /** Compacted term to canonical term, for the spaced-out scan. */
    private final Map<String, String> spacedBlocked;

    private ProfanityDictionary(
            Set<String> blockedTerms,
            Set<String> flaggedTerms
    ) {
        this.blocked = compile(blockedTerms);
        this.flagged = compile(flaggedTerms);
        this.anyBlocked = combine(blockedTerms);
        this.anyFlagged = combine(flaggedTerms);

        Map<String, String> spaced = new LinkedHashMap<>();
        for (String term : blockedTerms) {
            // Stored already collapsed, so that padding a letter as well as
            // spacing the word out -- "n i g g g e r" -- does not fall down
            // the gap between this scan and the ordinary one.
            spaced.put(collapseRuns(compact(term)), term);
        }
        this.spacedBlocked = Map.copyOf(spaced);
    }

    static ProfanityDictionary of(
            Set<String> blockedTerms,
            Set<String> flaggedTerms
    ) {
        return new ProfanityDictionary(
                fold(blockedTerms),
                fold(flaggedTerms)
        );
    }

    static ProfanityDictionary load(
            String blockedResource,
            String flaggedResource
    ) {
        return new ProfanityDictionary(
                readTerms(blockedResource),
                readTerms(flaggedResource)
        );
    }

    /**
     * Runs both lists over one piece of text.
     *
     * <p>Each list is checked with a single combined pattern first and only
     * broken down into per-term patterns when that hits. Almost everything
     * written on this platform is clean, and the common case should cost one
     * pass rather than one per word in the dictionary.
     */
    ProfanityVerdict scan(String text) {
        if (text == null || text.isBlank()) {
            return ProfanityVerdict.clean();
        }
        String normalized = unLeet(foldText(text));

        Set<String> blockedHits =
                new LinkedHashSet<>(hits(normalized, anyBlocked, blocked));
        blockedHits.addAll(spacedHits(normalized));

        return new ProfanityVerdict(
                List.copyOf(blockedHits),
                hits(normalized, anyFlagged, flagged)
        );
    }

    private static List<String> hits(
            String normalized,
            Pattern any,
            Map<String, Pattern> terms
    ) {
        if (any == null || !any.matcher(normalized).find()) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        terms.forEach((term, pattern) -> {
            if (pattern.matcher(normalized).find()) {
                found.add(term);
            }
        });
        return List.copyOf(found);
    }

    /**
     * Catches terms whose letters have been pushed apart, by pulling each
     * deliberately spaced-out run back together and looking again.
     */
    private List<String> spacedHits(String normalized) {
        if (spacedBlocked.isEmpty()) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        Matcher runs = SPACED_RUN.matcher(normalized);
        while (runs.find()) {
            String compacted = collapseRuns(compact(runs.group()));
            spacedBlocked.forEach((needle, term) -> {
                if (compacted.contains(needle) && !found.contains(term)) {
                    found.add(term);
                }
            });
        }
        return found;
    }

    private static Map<String, Pattern> compile(Set<String> terms) {
        Map<String, Pattern> compiled = new LinkedHashMap<>();
        for (String term : terms) {
            compiled.put(term, Pattern.compile(
                    LEFT_GUARD + body(term) + SUFFIXES + RIGHT_GUARD
            ));
        }
        return Map.copyOf(compiled);
    }

    private static Pattern combine(Set<String> terms) {
        if (terms.isEmpty()) {
            return null;
        }
        StringBuilder alternation = new StringBuilder();
        for (String term : terms) {
            if (!alternation.isEmpty()) {
                alternation.append('|');
            }
            alternation.append(body(term));
        }
        return Pattern.compile(
                LEFT_GUARD + "(?:" + alternation + ")" + SUFFIXES + RIGHT_GUARD
        );
    }

    /**
     * A term as a pattern, with every character allowed to repeat so that
     * "fuuuuck" and "shitttt" are the same word as far as this is concerned.
     */
    private static String body(String term) {
        StringBuilder body = new StringBuilder();
        term.codePoints().forEach(codePoint -> {
            body.append(Pattern.quote(
                    new String(Character.toChars(codePoint))
            ));
            if (Character.charCount(codePoint) == 1) {
                body.append('+');
            }
        });
        return body.toString();
    }

    // --- normalising ------------------------------------------------------

    private static Set<String> fold(Set<String> terms) {
        Set<String> folded = new LinkedHashSet<>();
        for (String term : terms) {
            folded.add(foldText(term));
        }
        return folded;
    }

    /**
     * Casing, compatibility forms, accents, invisible characters and
     * lookalike letters, all reduced to one spelling. Applied to the word
     * lists as well as to the text, so that an entry typed with a capital or
     * an accent still lines up with what the matcher sees.
     */
    private static String foldText(String value) {
        String precomposed = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(precomposed.length());
        precomposed.codePoints().forEach(codePoint -> {
            if (isIgnorable(codePoint)) {
                return;
            }
            int mapped = HOMOGLYPHS.getOrDefault(
                    codePoint,
                    stripAccent(codePoint)
            );
            out.appendCodePoint(Character.toLowerCase(mapped));
        });
        return out.toString();
    }

    private static int stripAccent(int codePoint) {
        if (codePoint < 0x00C0 || codePoint > 0x024F) {
            return codePoint;
        }
        String decomposed = Normalizer.normalize(
                new String(Character.toChars(codePoint)),
                Normalizer.Form.NFD
        );
        int base = decomposed.codePointAt(0);
        return base < 128 ? base : codePoint;
    }

    /**
     * Turns digits and symbols back into the letters they are standing in
     * for, but only within a token that already contains a Latin letter. A
     * run of pure digits is a version number, a port, a CVE or an ID, and
     * reading it as a word is how a filter starts rejecting bug reports.
     */
    private static String unLeet(String folded) {
        StringBuilder out = new StringBuilder(folded);
        Matcher matcher = TOKEN.matcher(folded);
        while (matcher.find()) {
            if (!ASCII_LETTER.matcher(matcher.group()).find()) {
                continue;
            }
            for (int i = matcher.start(); i < matcher.end(); i++) {
                Integer letter = LEET.get((int) out.charAt(i));
                if (letter != null) {
                    out.setCharAt(i, (char) letter.intValue());
                }
            }
        }
        return out.toString();
    }

    /**
     * Everything that is not a letter, a digit or a combining mark, removed.
     * Marks are kept because Khmer is assembled from them.
     */
    private static String compact(String value) {
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (Character.isLetterOrDigit(codePoint)
                    || Character.getType(codePoint)
                            == Character.NON_SPACING_MARK
                    || Character.getType(codePoint)
                            == Character.COMBINING_SPACING_MARK) {
                out.appendCodePoint(codePoint);
            }
        });
        return out.toString();
    }

    private static String collapseRuns(String value) {
        StringBuilder out = new StringBuilder(value.length());
        int previous = -1;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint != previous) {
                out.appendCodePoint(codePoint);
                previous = codePoint;
            }
        }
        return out.toString();
    }

    // --- loading ----------------------------------------------------------

    private static Set<String> readTerms(String resource) {
        try (InputStream stream =
                     ProfanityDictionary.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing word list on the classpath: " + resource
                );
            }
            Set<String> terms = new LinkedHashSet<>();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            String line;
            while ((line = reader.readLine()) != null) {
                String term = line.trim();
                if (term.isEmpty() || term.startsWith("#")) {
                    continue;
                }
                terms.add(foldText(term));
            }
            return terms;
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Could not read word list: " + resource,
                    exception
            );
        }
    }
}
