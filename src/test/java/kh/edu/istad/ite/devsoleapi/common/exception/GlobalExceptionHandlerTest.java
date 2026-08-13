package kh.edu.istad.ite.devsoleapi.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.executable.ExecutableValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives the advice through a real dispatcher with a stub controller, which is
 * the only way to see what a client actually receives: the failures worth
 * catching here are ones where the advice quietly turns a 403 or a 409 into a
 * 500, and no direct call on the handler would show that.
 */
class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "researcher",
                        "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }

    // ---------------------------------------------------------------- security

    @Test
    void methodSecurityDenialIsForbiddenNotServerError() throws Exception {
        authenticate();

        mockMvc.perform(get("/boom/authorization-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void plainAccessDeniedIsForbidden() throws Exception {
        authenticate();

        mockMvc.perform(get("/boom/access-denied"))
                .andExpect(status().isForbidden());
    }

    @Test
    void denialWithoutAuthenticationIsUnauthorised() throws Exception {
        // No login at all: 403 would tell the caller the door is shut when in
        // fact they never knocked.
        mockMvc.perform(get("/boom/access-denied"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- persistence

    @Test
    void uniqueConstraintBreachIsConflictNotServerError() throws Exception {
        mockMvc.perform(get("/boom/duplicate"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void constraintBreachDoesNotLeakTheDatabaseMessage() throws Exception {
        mockMvc.perform(get("/boom/duplicate"))
                .andExpect(jsonPath("$.message")
                        .value(not(containsString("uq_recognitions_report"))));
    }

    @Test
    void entityValidationAtFlushIsReportedAsBadRequest() throws Exception {
        mockMvc.perform(get("/boom/flush-validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetails.title").isNotEmpty());
    }

    @Test
    void otherTransactionFailuresStayServerErrors() throws Exception {
        mockMvc.perform(get("/boom/transaction"))
                .andExpect(status().isInternalServerError());
    }

    // -------------------------------------------------------------- requests

    @Test
    void statusOnlyFailureStillCarriesAMessage() throws Exception {
        // new ResponseStatusException(NOT_FOUND) has no reason, and the body
        // used to go out with "message": null.
        mockMvc.perform(get("/boom/reasonless"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void nonStandardStatusCodeDoesNotBreakTheHandler() throws Exception {
        mockMvc.perform(get("/boom/odd-status"))
                .andExpect(status().is(499))
                .andExpect(jsonPath("$.code").value(499));
    }

    @Test
    void parameterViolationNamesTheParameterNotTheMethod() throws Exception {
        mockMvc.perform(get("/boom/validated"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetails.size").isNotEmpty());
    }

    @Test
    void malformedPathVariableIsBadRequest() throws Exception {
        mockMvc.perform(get("/boom/by-id/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetails.id")
                        .value(containsString("UUID")));
    }

    @Test
    void malformedJsonBodyIsBadRequest() throws Exception {
        mockMvc.perform(post("/boom/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(not(containsString("com.fasterxml"))));
    }

    @Test
    void wrongMethodKeepsItsOwnStatus() throws Exception {
        mockMvc.perform(post("/boom/not-found"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ---------------------------------------------------------------- shape

    @Test
    void errorBodyCarriesTheRequestPath() throws Exception {
        mockMvc.perform(get("/boom/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/boom/not-found"));
    }

    @Test
    void unexpectedFailureIsOpaqueButTraceable() throws Exception {
        mockMvc.perform(get("/boom/unexpected"))
                .andExpect(status().isInternalServerError())
                // the client must not be told about our internals, but it needs
                // something to quote when reporting the failure
                .andExpect(jsonPath("$.message")
                        .value(not(containsString("ArithmeticException"))))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    // ----------------------------------------------------------------- stub

    /**
     * Builds the exception bean validation raises for a rejected method
     * parameter, so the property-path trimming is exercised against a real
     * violation rather than a hand-made one.
     */
    private static ConstraintViolationException methodValidationFailure() {
        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {

            ExecutableValidator executableValidator =
                    factory.getValidator().forExecutables();

            Method method = ThrowingController.class
                    .getDeclaredMethod("validated", int.class);

            Set<ConstraintViolation<ThrowingController>> violations =
                    executableValidator.validateParameters(
                            new ThrowingController(),
                            method,
                            new Object[]{9999}
                    );

            return new ConstraintViolationException(violations);
        } catch (NoSuchMethodException cannotHappen) {
            throw new IllegalStateException(cannotHappen);
        }
    }

    private static ConstraintViolationException entityValidationFailure() {
        try (ValidatorFactory factory =
                     Validation.buildDefaultValidatorFactory()) {
            return new ConstraintViolationException(
                    factory.getValidator().validate(new StubEntity())
            );
        }
    }

    static class StubEntity {
        @NotBlank
        private String title;
    }

    record Body(String value) {
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/boom/authorization-denied")
        void authorizationDenied() {
            throw new AuthorizationDeniedException("Access Denied");
        }

        @GetMapping("/boom/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("Access Denied");
        }

        @GetMapping("/boom/duplicate")
        void duplicate() {
            throw new DataIntegrityViolationException(
                    "ERROR: duplicate key value violates unique constraint "
                            + "\"uq_recognitions_report\""
            );
        }

        @GetMapping("/boom/flush-validation")
        void flushValidation() {
            throw new TransactionSystemException(
                    "Could not commit JPA transaction",
                    entityValidationFailure()
            );
        }

        @GetMapping("/boom/transaction")
        void transaction() {
            throw new TransactionSystemException(
                    "Could not commit JPA transaction",
                    new IllegalStateException("connection reset")
            );
        }

        @GetMapping("/boom/reasonless")
        void reasonless() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        @GetMapping("/boom/odd-status")
        void oddStatus() {
            throw new ResponseStatusException(
                    HttpStatusCode.valueOf(499),
                    "Client closed the request"
            );
        }

        @GetMapping("/boom/validated")
        void triggerValidation() {
            throw methodValidationFailure();
        }

        void validated(@RequestParam @Min(1) @Max(100) int size) {
        }

        @GetMapping("/boom/by-id/{id}")
        void byId(@PathVariable UUID id) {
        }

        @PostMapping("/boom/body")
        void body(@RequestBody Body body) {
        }

        @GetMapping("/boom/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Report not found: 42");
        }

        @GetMapping("/boom/unexpected")
        void unexpected() {
            throw new ArithmeticException("/ by zero");
        }
    }
}
