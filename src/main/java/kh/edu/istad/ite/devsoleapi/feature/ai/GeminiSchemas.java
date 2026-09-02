package kh.edu.istad.ite.devsoleapi.feature.ai;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a Java record into the {@code responseSchema} Gemini constrains its
 * answer to.
 *
 * <p>Derived rather than hand-written, so the schema cannot drift from the
 * record it is parsed back into. A hand-written copy would be one edit away
 * from asking for a field that no longer exists — and the failure mode of that
 * is not a compiler error, it is a model dutifully returning a field nothing
 * reads.
 *
 * <p>Only the subset of Java this application actually sends is supported, and
 * anything else throws rather than guessing. A record gaining a {@code Map} or
 * an {@code Optional} field should fail loudly in a test, not silently produce
 * a schema that omits it.
 */
final class GeminiSchemas {

    private GeminiSchemas() {
    }

    static Map<String, Object> of(Class<?> shape) {
        return schema(shape, shape);
    }

    /**
     * @param generic the declared type, which carries the element type of a
     *                list — {@code type} alone has been erased to
     *                {@code List.class} and cannot say what is in it
     */
    private static Map<String, Object> schema(Class<?> type, Type generic) {
        if (type == String.class) {
            return node("STRING");
        }
        if (type == boolean.class || type == Boolean.class) {
            return node("BOOLEAN");
        }
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class) {
            return node("INTEGER");
        }
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            return node("NUMBER");
        }
        if (type.isEnum()) {
            Map<String, Object> schema = node("STRING");
            schema.put("enum", List.of(type.getEnumConstants()).stream()
                    .map(constant -> ((Enum<?>) constant).name())
                    .toList());
            return schema;
        }
        if (List.class.isAssignableFrom(type)) {
            Class<?> element = elementOf(generic);
            Map<String, Object> schema = node("ARRAY");
            schema.put("items", schema(element, element));
            return schema;
        }
        if (type.isRecord()) {
            return objectSchema(type);
        }
        throw new IllegalArgumentException(
                "No Gemini schema mapping for " + type.getName()
        );
    }

    /**
     * Every component is required and the order is pinned.
     *
     * <p>Both on purpose. An optional field invites the model to leave out the
     * one that carries the reasoning, and {@code propertyOrdering} is what stops
     * the same question producing a differently ordered — and so differently
     * cached, differently diffed — answer between calls.
     */
    private static Map<String, Object> objectSchema(Class<?> type) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> order = new ArrayList<>();

        for (RecordComponent component : type.getRecordComponents()) {
            properties.put(
                    component.getName(),
                    schema(component.getType(), component.getGenericType())
            );
            order.add(component.getName());
        }

        Map<String, Object> schema = node("OBJECT");
        schema.put("properties", properties);
        schema.put("required", order);
        schema.put("propertyOrdering", order);
        return schema;
    }

    private static Class<?> elementOf(Type generic) {
        if (generic instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments().length == 1
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        throw new IllegalArgumentException(
                "A list in a response shape needs a concrete element type, got "
                        + generic
        );
    }

    private static Map<String, Object> node(String type) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        return schema;
    }
}
