package kh.edu.istad.ite.devsoleapi.common.content;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The matching, on a vocabulary this test owns.
 *
 * <p>Deliberately not the shipped word lists. What is being checked here is
 * that "sh1t" and "shit" are the same word and "class" is not, and those
 * facts should not stop being covered the day somebody edits a list.
 */
class ProfanityDictionaryTest {

    private static final ProfanityDictionary DICTIONARY =
            ProfanityDictionary.of(
                    Set.of("nigger", "faggot"),
                    Set.of("shit", "ass", "fuck")
            );

    private static ProfanityVerdict scan(String text) {
        return DICTIONARY.scan(text);
    }

    @Test
    void ordinaryWritingIsClean() {
        ProfanityVerdict verdict = scan(
                "The endpoint returns 500 when the token has expired."
        );
        assertFalse(verdict.isBlocked());
        assertFalse(verdict.isFlagged());
    }

    @Test
    void severityDecidesWhichListATermLandsIn() {
        assertTrue(scan("what a nigger").isBlocked());

        ProfanityVerdict mild = scan("this build is shit");
        assertTrue(mild.isFlagged());
        assertFalse(mild.isBlocked());
        assertEquals("shit", mild.flagged().getFirst());
    }

    @Test
    void aTermInsideALongerWordIsNotAMatch() {
        // The whole Scunthorpe family, plus the ones this platform types
        // every day.
        for (String innocent : new String[]{
                "class", "classes", "assess", "assert", "assertion",
                "analysis", "assassin", "pass", "bass", "massive",
                "assembly", "association", "assign", "passed"
        }) {
            assertFalse(
                    scan("The " + innocent + " is fine.").isFlagged(),
                    innocent + " should not match"
            );
        }
    }

    @Test
    void paddedAndLeetSpellingsStillMatch() {
        assertTrue(scan("fuuuuuck this").isFlagged());
        assertTrue(scan("sh1t").isFlagged());
        assertTrue(scan("$hit").isFlagged());
        assertTrue(scan("@ss").isFlagged());
        assertTrue(scan("FUCK").isFlagged());
        assertTrue(scan("fück").isFlagged());
    }

    @Test
    void lookalikeLettersFromOtherAlphabetsStillMatch() {
        // Written as escapes because the whole point is that these are
        // indistinguishable from the Latin letters on screen.
        assertTrue(scan("аss").isFlagged());        // Cyrillic a
        assertTrue(scan("fuсk").isFlagged());       // Cyrillic c
        assertTrue(scan("ѕhit").isFlagged());       // Cyrillic s
        assertTrue(scan("shіt").isFlagged());       // Cyrillic i
    }

    @Test
    void invisibleCharactersDoNotBreakATermUp() {
        assertTrue(scan("f​u​c​k").isFlagged());
    }

    @Test
    void commonSuffixesAreCovered() {
        assertTrue(scan("fucking hell").isFlagged());
        assertTrue(scan("what a fucker").isFlagged());
        assertTrue(scan("two faggots").isBlocked());
        assertTrue(scan("shitty code").isFlagged());
    }

    @Test
    void digitsThatAreJustDigitsAreLeftAlone() {
        // 4 5 5 folds to a s s only inside a token that already holds a
        // letter. A platform that reads CVE identifiers as slurs is worse
        // than one with no filter at all.
        assertFalse(scan("Tracked as CVE-2021-455").isFlagged());
        assertFalse(scan("Build 45500 failed").isFlagged());
        assertFalse(scan("port 4550").isFlagged());
    }

    @Test
    void aHexDigestIsNotAHit() {
        assertFalse(
                scan("sha256 was deadbeef4550cafe0badf00d").isFlagged()
        );
    }

    @Test
    void slursSpacedOutAreStillCaught() {
        assertTrue(scan("n i g g e r").isBlocked());
        assertTrue(scan("f.a.g.g.o.t").isBlocked());
        assertTrue(scan("n i g g g e r").isBlocked());
    }

    @Test
    void ordinaryWordsAreNotJoinedIntoATerm() {
        // The trap the spaced-out scan sets for itself: delete every
        // separator in the text and two innocent words assemble a term
        // between them. Only runs already spelled out one letter at a time
        // are pulled together, so these stay clean.
        assertFalse(scan("sure tardy").isBlocked());
        assertFalse(scan("a moral panic").isBlocked());
        assertFalse(scan("campus system").isFlagged());
    }

    @Test
    void spacingOutIsOnlyChasedForTheBlockedList() {
        // Deliberately spaced, but "ass" is on the milder list, where the
        // cost of chasing it is not worth paying.
        assertFalse(scan("a s s h o l e").isFlagged());
    }

    @Test
    void aTermIsReportedByItsCanonicalSpelling() {
        // What goes in front of a moderator is the dictionary entry, not
        // whatever the author typed to get around it.
        assertEquals("shit", scan("$h1ttt").flagged().getFirst());
    }

    @Test
    void everyMatchingTermIsReported() {
        ProfanityVerdict verdict = scan("shit and fuck");
        assertEquals(2, verdict.flagged().size());
        assertTrue(verdict.flagged().containsAll(Set.of("shit", "fuck")));
    }
}
