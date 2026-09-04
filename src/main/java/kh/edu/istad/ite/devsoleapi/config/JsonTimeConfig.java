package kh.edu.istad.ite.devsoleapi.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Says out loud that every timestamp this API produces is UTC.
 *
 * <p>Almost every column here is a {@code LocalDateTime} over a
 * {@code timestamp without time zone}, written by a container with no
 * {@code TZ} set and therefore running in UTC. The values were always right;
 * what was missing is that the JSON never said which zone they were in — a bare
 * {@code 2026-09-04T01:40:20} is read by every browser as the *viewer's* local
 * time, so a reader in Phnom Penh saw every timestamp seven hours early.
 *
 * <p>Fixed at the edge rather than by migrating the columns. What is stored is
 * already UTC, so nothing needs rewriting and no entity changes — this only
 * stops the API hiding what it already knows. Moving to {@code timestamptz}
 * throughout is the real cleanup, and a far larger change.
 *
 * <p>Reading is deliberately looser than writing: an offset is accepted and
 * converted, so a client that sends back a timestamp this API gave it — a draft
 * reloaded and saved again, and {@code PUT /report-drafts/{id}} replaces the
 * whole document — is not rejected for repeating our own format.
 *
 * <p>Registered against Jackson 3's {@code JsonMapper}, which is what Spring
 * Boot 4 serializes HTTP with. Jackson 2 is also on the classpath, and a
 * serializer registered there would be quietly ignored by the web layer.
 */
@Configuration
public class JsonTimeConfig {

    @Bean
    public JsonMapperBuilderCustomizer utcLocalDateTimeCustomizer() {
        SimpleModule module = new SimpleModule("utc-local-date-time");
        module.addSerializer(
                LocalDateTime.class,
                new UtcLocalDateTimeSerializer()
        );
        module.addDeserializer(
                LocalDateTime.class,
                new UtcLocalDateTimeDeserializer()
        );
        return builder -> builder.addModule(module);
    }

    /**
     * Labels the value UTC rather than converting it. The wall clock already is
     * UTC — {@code atOffset} attaches the offset it was written in and does not
     * shift the moment.
     */
    static final class UtcLocalDateTimeSerializer
            extends ValueSerializer<LocalDateTime> {

        @Override
        public void serialize(
                LocalDateTime value,
                JsonGenerator generator,
                SerializationContext context
        ) throws JacksonException {
            // Milliseconds, not the nanoseconds Postgres hands back. Nothing
            // downstream reads past the millisecond, and a nine-digit fraction
            // is the part of an ISO string client date parsers get wrong.
            generator.writeString(
                    value.truncatedTo(ChronoUnit.MILLIS)
                            .atOffset(ZoneOffset.UTC)
                            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            );
        }
    }

    /**
     * Accepts both shapes: a bare local date-time, as clients sent before this
     * existed, and one carrying an offset, which is what they now receive.
     */
    static final class UtcLocalDateTimeDeserializer
            extends ValueDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(
                JsonParser parser,
                DeserializationContext context
        ) throws JacksonException {
            String text = parser.getString();
            if (text == null || text.isBlank()) {
                return null;
            }
            String trimmed = text.trim();
            try {
                // An offset means the client named a moment. Convert it to the
                // UTC wall clock the column holds, so 15:00+07:00 is stored as
                // 08:00 rather than a literal 15:00 nobody can interpret later.
                return OffsetDateTime.parse(trimmed)
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
            } catch (DateTimeParseException noOffset) {
                return LocalDateTime.parse(trimmed);
            }
        }
    }
}
