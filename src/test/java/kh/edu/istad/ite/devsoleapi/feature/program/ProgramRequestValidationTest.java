package kh.edu.istad.ite.devsoleapi.feature.program;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramGuidelinesDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetRequestDto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgramRequestValidationTest {

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

    @Test
    void validProgramRequestPassesValidation() {
        ProgramRequestDto request = new ProgramRequestDto(
                "acme-security",
                "Acme Security Program",
                "Security research program",
                EngagementType.BOUNTY,
                Visibility.PRIVATE,
                "Only test assets explicitly listed as in scope.",
                "Include reproducible steps, affected endpoints, and evidence.",
                guidelines("Follow the testing rules", "Test only in scope"),
                guidelines("These findings are excluded", "Self-XSS"),
                false,
                null,
                null,
                List.of(new ProgramAssetRequestDto(
                        AssetType.URL,
                        "https://app.acme.test",
                        null,
                        true,
                        null
                )),
                List.of()
        );

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    void invalidProgramSetupReportsRequiredFieldsAndNestedErrors() {
        ProgramRequestDto request = new ProgramRequestDto(
                "Invalid Handle",
                " ",
                null,
                null,
                Visibility.PRIVATE,
                " ",
                " ",
                null,
                null,
                false,
                null,
                null,
                List.of(new ProgramAssetRequestDto(
                        null,
                        " ",
                        null,
                        null,
                        null
                )),
                List.of()
        );

        Set<ConstraintViolation<ProgramRequestDto>> violations =
                validator.validate(request);

        assertEquals(11, violations.size());
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("handle")
        ));
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString()
                        .equals("assets[0].assetType")
        ));
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString()
                        .equals("proofOfConceptRequirements")
        ));
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString()
                        .equals("rulesOfEngagement")
        ));
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString()
                        .equals("exclusions")
        ));
    }

    @Test
    void guidelinesRejectBlankDescriptionAndRules() {
        ProgramRequestDto request = new ProgramRequestDto(
                "acme-security",
                "Acme Security Program",
                null,
                EngagementType.RESPONSE,
                Visibility.PRIVATE,
                "Test only assets explicitly listed as in scope.",
                "Include reproducible steps and evidence.",
                new ProgramGuidelinesDto(" ", List.of(" ")),
                guidelines("These findings are excluded", "Self-XSS"),
                false,
                null,
                null,
                List.of(new ProgramAssetRequestDto(
                        AssetType.URL,
                        "https://app.acme.test",
                        null,
                        true,
                        null
                )),
                List.of()
        );

        Set<ConstraintViolation<ProgramRequestDto>> violations =
                validator.validate(request);

        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString()
                        .equals("rulesOfEngagement.description")
        ));
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString()
                        .startsWith("rulesOfEngagement.rules[0]")
        ));
    }

    private ProgramGuidelinesDto guidelines(
            String description,
            String rule
    ) {
        return new ProgramGuidelinesDto(description, List.of(rule));
    }
}
