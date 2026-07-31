package kh.edu.istad.ite.devsoleapi.feature.vote;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoteRequestValidationTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsOnlyUpvoteOrDownvote() {
        assertTrue(validator.validate(
                new VoteRequest((short) 1)
        ).isEmpty());
        assertTrue(validator.validate(
                new VoteRequest((short) -1)
        ).isEmpty());
        assertFalse(validator.validate(
                new VoteRequest((short) 0)
        ).isEmpty());
        assertFalse(validator.validate(
                new VoteRequest(null)
        ).isEmpty());
    }
}
