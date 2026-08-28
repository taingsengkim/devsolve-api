package kh.edu.istad.ite.devsoleapi.feature.userprofile.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsernamePolicyTest {

    @Test
    void separatorsAreAllowedBetweenButNotAtEitherEnd() {
        assertTrue(UsernamePolicy.isValid("sengkim"));
        assertTrue(UsernamePolicy.isValid("taing.sengkim"));
        assertTrue(UsernamePolicy.isValid("taing_seng-kim3110"));
        assertTrue(UsernamePolicy.isValid("a1b"));

        assertFalse(UsernamePolicy.isValid(".hidden"));
        assertFalse(UsernamePolicy.isValid("trailing-"));
        assertFalse(UsernamePolicy.isValid("_lead"));
        assertFalse(UsernamePolicy.isValid("has space"));
        assertFalse(UsernamePolicy.isValid("emoji🙂"));
        assertFalse(UsernamePolicy.isValid(null));
    }

    @Test
    void lengthIsBounded() {
        assertFalse(UsernamePolicy.isValid("ab"));
        assertTrue(UsernamePolicy.isValid("a".repeat(30)));
        assertFalse(UsernamePolicy.isValid("a".repeat(31)));
    }

    @Test
    void reservationIgnoresCase() {
        assertTrue(UsernamePolicy.isReserved("admin"));
        assertTrue(UsernamePolicy.isReserved("Admin"));
        assertTrue(UsernamePolicy.isReserved("ME"));
        assertTrue(UsernamePolicy.isReserved("devsolve"));
        assertFalse(UsernamePolicy.isReserved("sengkim"));
    }

    @Test
    void suggestionsAreDerivedFromTheEmailAndAlwaysUsable() {
        assertEquals("sengkim", UsernamePolicy.suggestFrom("Sengkim@acme.com"));
        assertEquals(
                "taing.sengkim",
                UsernamePolicy.suggestFrom("taing.sengkim@acme.com")
        );
        // Characters the handle cannot hold are dropped, and the separators
        // that would be left stranded at either end go with them.
        assertEquals("a.b", UsernamePolicy.suggestFrom("_a.b_@acme.com"));
        // Dropping the illegal character can leave too little to work with,
        // and what is left then has to give way to the fallback.
        assertEquals("member", UsernamePolicy.suggestFrom("_a+b_@acme.com"));

        // Nothing usable survives, and "user" is reserved, so the fallback has
        // to be a name somebody can actually keep.
        assertEquals("member", UsernamePolicy.suggestFrom("+@acme.com"));
        assertFalse(UsernamePolicy.isReserved(
                UsernamePolicy.suggestFrom("+@acme.com")
        ));

        String fromLongEmail = UsernamePolicy.suggestFrom(
                "a".repeat(60) + "@acme.com"
        );
        assertTrue(UsernamePolicy.isValid(fromLongEmail));
    }

    @Test
    void numberingStaysInsideTheLengthLimitAndStaysValid() {
        assertEquals("sengkim2", UsernamePolicy.withSuffix("sengkim", 2));

        String numbered = UsernamePolicy.withSuffix("a".repeat(30), 12);
        assertEquals(30, numbered.length());
        assertTrue(UsernamePolicy.isValid(numbered));

        // Truncating must not leave a separator at the end, which would make
        // the numbered handle fail the very rule the original passed.
        assertTrue(UsernamePolicy.isValid(
                UsernamePolicy.withSuffix("a".repeat(28) + ".b", 7)
        ));
    }

    @Test
    void comparisonIsCaseInsensitive() {
        assertEquals(
                UsernamePolicy.normalize("Sengkim"),
                UsernamePolicy.normalize("sengkim")
        );
    }
}
