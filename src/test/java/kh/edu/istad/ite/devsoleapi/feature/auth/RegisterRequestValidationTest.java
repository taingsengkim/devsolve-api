package kh.edu.istad.ite.devsoleapi.feature.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import kh.edu.istad.ite.devsoleapi.feature.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterRequestValidationTest {

    private final Validator validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();

    @Test
    void acceptsValidUserRegistration() {
        assertTrue(validator.validate(request(
                "Password123!",
                "Password123!"
        )).isEmpty());
    }

    @Test
    void rejectsBlankOrShortPasswords() {
        assertFalse(validator.validate(request("", "")).isEmpty());
        assertFalse(validator.validate(request("short", "short")).isEmpty());
    }

    private RegisterRequest request(String password, String confirmation) {
        return new RegisterRequest(
                "security.researcher",
                password,
                confirmation,
                "researcher@example.com",
                "Security",
                "Researcher",
                "+85512345678",
                RoleEnum.USER
        );
    }
}
