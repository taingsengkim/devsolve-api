package kh.edu.istad.ite.devsoleapi.feature.organization;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import kh.edu.istad.ite.devsoleapi.feature.organization.dto.OrganizationRequest;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.Industry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The phone field is checked in two places — shape here, digit count in the
 * service once the punctuation is off — so both halves are covered.
 */
class OrganizationRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    /** The shapes a person actually types into a phone field. */
    @ParameterizedTest
    @ValueSource(strings = {
            "+85512345678",
            "+855 12 345 678",
            "023999111",
            "(023) 999-111",
            "023.999.111",
            "+1 (555) 010-9999"
    })
    void aPhoneNumberIsAcceptedHoweverItIsPunctuated(String phone) {
        assertTrue(validator.validate(requestWithPhone(phone)).isEmpty(), phone);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-a-number",
            "+",
            "++85512345678",
            "12345678a",
            "ext. 4412"
    })
    void aPhoneNumberThatIsNotOneIsRejected(String phone) {
        Set<ConstraintViolation<OrganizationRequest>> violations =
                validator.validate(requestWithPhone(phone));

        assertEquals(1, violations.size(), phone);
        assertEquals("phone", violations.iterator().next()
                .getPropertyPath().toString());
    }

    @Test
    void aMissingPhoneNumberIsRejected() {
        Set<ConstraintViolation<OrganizationRequest>> violations =
                validator.validate(requestWithPhone("   "));

        assertTrue(violations.stream().anyMatch(violation ->
                "phone".equals(violation.getPropertyPath().toString())
        ));
    }

    @Test
    void aCompleteRequestPassesValidation() {
        assertTrue(
                validator.validate(requestWithPhone("+85512345678")).isEmpty()
        );
    }

    private OrganizationRequest requestWithPhone(String phone) {
        return new OrganizationRequest(
                "Acme Owner",
                "Security Manager",
                "owner@acme.com",
                phone,
                "Password123!",
                "Password123!",
                "Acme Security",
                "https://acme.com",
                Industry.TECHNOLOGY,
                "11-50",
                "Cambodia",
                "We want to run a responsible disclosure program."
        );
    }
}
