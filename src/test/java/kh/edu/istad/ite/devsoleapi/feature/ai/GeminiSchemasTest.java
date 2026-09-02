package kh.edu.istad.ite.devsoleapi.feature.ai;

import kh.edu.istad.ite.devsoleapi.feature.problem.DuplicateJudgements;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiSchemasTest {

    /**
     * The schema that actually goes over the wire. Asserted in full because
     * this is the coupling that can rot silently: a component renamed on the
     * record still compiles, still parses, and quietly asks the model for a
     * field nobody reads.
     */
    @Test
    @SuppressWarnings("unchecked")
    void describesTheJudgementRecordTheModelHasToFill() {
        Map<String, Object> schema = GeminiSchemas.of(DuplicateJudgements.class);

        assertEquals("OBJECT", schema.get("type"));
        assertEquals(List.of("matches"), schema.get("required"));

        Map<String, Object> matches = (Map<String, Object>)
                ((Map<String, Object>) schema.get("properties")).get("matches");
        assertEquals("ARRAY", matches.get("type"));

        Map<String, Object> judgement = (Map<String, Object>) matches.get("items");
        assertEquals("OBJECT", judgement.get("type"));
        assertEquals(
                List.of("id", "verdict", "confidence", "reason"),
                judgement.get("required")
        );

        Map<String, Object> properties =
                (Map<String, Object>) judgement.get("properties");
        assertEquals("STRING", ((Map<String, Object>) properties.get("id")).get("type"));
        assertEquals("STRING", ((Map<String, Object>) properties.get("reason")).get("type"));
        assertEquals(
                "INTEGER",
                ((Map<String, Object>) properties.get("confidence")).get("type")
        );
    }

    /**
     * The verdict is the field the response is sorted and rendered on, so the
     * model is given the vocabulary rather than trusted to invent it.
     */
    @Test
    @SuppressWarnings("unchecked")
    void pinsTheVerdictToTheEnumConstantsThatExist() {
        Map<String, Object> schema = GeminiSchemas.of(DuplicateJudgements.class);

        Map<String, Object> matches = (Map<String, Object>)
                ((Map<String, Object>) schema.get("properties")).get("matches");
        Map<String, Object> properties = (Map<String, Object>)
                ((Map<String, Object>) matches.get("items")).get("properties");
        Map<String, Object> verdict = (Map<String, Object>) properties.get("verdict");

        assertEquals("STRING", verdict.get("type"));
        assertEquals(
                List.of("DUPLICATE", "NEAR_DUPLICATE", "RELATED"),
                verdict.get("enum")
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void pinsPropertyOrderSoTheSameQuestionGetsTheSameShapedAnswer() {
        Map<String, Object> schema = GeminiSchemas.of(DuplicateJudgements.class);

        assertEquals(schema.get("required"), schema.get("propertyOrdering"));
    }

    /** A record gaining an unsupported field should fail here, not in production. */
    @Test
    void refusesAShapeItCannotDescribe() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> GeminiSchemas.of(Unsupported.class)
        );

        assertTrue(failure.getMessage().contains("Map"));
    }

    record Unsupported(Map<String, String> attributes) {
    }
}
