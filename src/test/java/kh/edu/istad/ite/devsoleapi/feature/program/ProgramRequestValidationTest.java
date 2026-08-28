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
                null,
                "Only test assets explicitly listed as in scope.",
                guidelines(
                        "Include reproducible steps and evidence",
                        "Attach the affected endpoints"
                ),
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

    /**
     * A wizard with a "Save draft" button on every step cannot satisfy the
     * published-program contract on step one, and the only way it could was by
     * inventing the answers to the later steps. An invented policy nobody
     * revisits is the policy researchers end up bound by, so absence has to be
     * expressible. Completeness is checked at submission instead.
     */
    @Test
    void aDraftMayCarryNothingButAHandleAndAName() {
        ProgramRequestDto request = ProgramRequestDto.builder()
                .handle("acme-security")
                .name("Acme Security Program")
                .build();

        assertTrue(validator.validate(request).isEmpty());
    }

    /**
     * Absence is what a draft is allowed; nonsense is not. Nothing here is
     * merely missing — every value supplied is one the API could not store.
     */
    @Test
    void aDraftIsStillHeldToTheFormatOfWhateverItSupplies() {
        ProgramRequestDto request = new ProgramRequestDto(
                "Invalid Handle",
                " ",
                null,
                null,
                Visibility.PRIVATE,
                null,
                null,
                null,
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

        assertEquals(6, violations.size());
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().equals("handle")
        ));
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString()
                        .equals("assets[0].assetType")
        ));
        // The three guideline blocks are absent, not wrong, and a draft may
        // leave them so.
        assertTrue(violations.stream().noneMatch(violation ->
                violation.getPropertyPath().toString()
                        .startsWith("proofOfConceptRequirements")
                        || violation.getPropertyPath().toString()
                        .startsWith("rulesOfEngagement")
                        || violation.getPropertyPath().toString()
                        .startsWith("exclusions")
        ));
    }

    @Test
    void guidelinesAcceptAMissingDescriptionButNotABlankRule() {
        ProgramRequestDto request = new ProgramRequestDto(
                "acme-security",
                "Acme Security Program",
                null,
                EngagementType.RESPONSE,
                Visibility.PRIVATE,
                null,
                "Test only assets explicitly listed as in scope.",
                guidelines(
                        "Include reproducible steps and evidence",
                        "Attach a request trace"
                ),
                new ProgramGuidelinesDto(null, List.of(" ")),
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

        // A description not written yet is a step the author has not reached.
        assertTrue(violations.stream().noneMatch(violation ->
                violation.getPropertyPath().toString()
                        .equals("rulesOfEngagement.description")
        ));
        // A rule that is only whitespace is one they cannot have meant.
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
