package kh.edu.istad.ite.devsoleapi.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * One rejected field, with the rule it broke.
 *
 * <p>{@code errorDetails} already carries the field and a sentence, which is
 * enough to show the user but not enough to validate against — so a client
 * keeps its own copy of the length and pattern, and that copy is wrong from
 * the day either changes here. {@code constraint} and {@code rule} are the
 * constraint's own parameters, read off the annotation that rejected the
 * value, so there is nothing left to duplicate.
 *
 * @param constraint the annotation that rejected it: {@code Pattern},
 *                   {@code Size}, {@code NotBlank}, and so on.
 * @param rule       that annotation's parameters — {@code regexp} for a
 *                   Pattern, {@code min}/{@code max} for a Size. Empty when
 *                   the constraint has none to give.
 */
@Schema(description = "A rejected field and the constraint it broke")
public record FieldViolation(
        String field,
        String message,
        String constraint,
        Map<String, Object> rule
) {
}
