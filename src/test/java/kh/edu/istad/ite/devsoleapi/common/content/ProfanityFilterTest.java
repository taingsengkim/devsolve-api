package kh.edu.istad.ite.devsoleapi.common.content;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped word lists, and the decision about which parts of a markdown
 * document they are allowed to see.
 */
class ProfanityFilterTest {

    @Test
    void aBugReportIsClean() {
        ProfanityVerdict verdict = ProfanityFilter.scan(
                "Login fails after the session expires",
                "Steps: sign in, wait an hour, refresh. The class that "
                        + "handles the refresh asserts on a token it no "
                        + "longer has, and the assertion takes the page "
                        + "down. Analysis of the heap dump attached."
        );
        assertFalse(verdict.isBlocked());
        assertFalse(verdict.isFlagged());
    }

    @Test
    void theTwoTiersAreSeparate() {
        assertTrue(ProfanityFilter.scan("you retard").isBlocked());

        ProfanityVerdict mild = ProfanityFilter.scan("this is shit");
        assertTrue(mild.isFlagged());
        assertFalse(mild.isBlocked());
    }

    @Test
    void fencedCodeIsNotProse() {
        // The author did not write this, and a payload will eventually
        // spell something.
        ProfanityVerdict verdict = ProfanityFilter.scan(
                "The request body that reproduces it:\n"
                        + "```\n"
                        + "{\"user\": \"shit\", \"role\": \"fuck\"}\n"
                        + "```\n"
                        + "and the response is a 500."
        );
        assertFalse(verdict.isFlagged());
    }

    @Test
    void inlineCodeIsNotProse() {
        assertFalse(
                ProfanityFilter.scan("the `shit` variable is misnamed")
                        .isFlagged()
        );
    }

    @Test
    void linksAreNotProse() {
        assertFalse(
                ProfanityFilter.scan("see https://example.com/shit/readme")
                        .isFlagged()
        );
    }

    @Test
    void anUnterminatedFenceDoesNotSwitchTheFilterOff() {
        // Treating a lone ``` as opening a block that runs to the end of
        // the document would be a one-line bypass anybody could find.
        assertTrue(
                ProfanityFilter.scan("```\nyou fucking idiot").isFlagged()
        );
    }

    @Test
    void proseEitherSideOfARemovedSpanDoesNotRunTogether() {
        // Cuts leave a space behind, so "a" and "ss" cannot become "ass".
        assertFalse(ProfanityFilter.scan("a `x` ss").isFlagged());
    }

    @Test
    void normalizeTextRefusesASlur() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> ContentSafetyRules.normalizeText(
                        "you faggot",
                        "Comment"
                )
        );
        assertTrue(exception.getMessage().contains("not allowed"));
        // The term is not repeated back.
        assertFalse(exception.getMessage().contains("faggot"));
    }

    @Test
    void normalizeTextLetsOrdinarySwearingThrough() {
        assertDoesNotThrow(() -> ContentSafetyRules.normalizeText(
                "this shit never works",
                "Comment"
        ));
    }

    @Test
    void profanityReturnsOnlyTheFlaggedHalf() {
        assertTrue(ContentSafetyRules.profanity("what a bastard")
                .contains("bastard"));
        assertTrue(ContentSafetyRules.profanity("nothing wrong here")
                .isEmpty());
    }
}
