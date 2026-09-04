package kh.edu.istad.ite.devsoleapi.config;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Timestamps have to say which zone they are in.
 *
 * <p>The values were always UTC; a bare ISO string just did not say so, and a
 * browser reads one as the viewer's own local time. Seven hours out in Phnom
 * Penh, and silently — nothing errors, the page simply lies.
 *
 * <p>Built against Jackson 3's {@code JsonMapper}, which is what Spring Boot 4
 * serializes HTTP with. Jackson 2 is on the classpath too, so a test that
 * proved this against the wrong mapper would pass while the API kept emitting
 * bare timestamps.
 */
class JsonTimeConfigTest {

    private final JsonMapper mapper = mapper();

    @Test
    void aTimestampIsWrittenAsUtcRatherThanBare() {
        String json = mapper.writeValueAsString(
                new Holder(LocalDateTime.of(2026, 9, 4, 1, 40, 20))
        );

        assertEquals("{\"at\":\"2026-09-04T01:40:20Z\"}", json);
    }

    /**
     * Postgres hands back nanoseconds. Nothing downstream reads past the
     * millisecond, and the long fraction is what client parsers get wrong.
     */
    @Test
    void nanosecondsAreTrimmedToMilliseconds() {
        String json = mapper.writeValueAsString(
                new Holder(
                        LocalDateTime.of(2026, 9, 4, 1, 40, 20, 179_640_873)
                )
        );

        assertEquals("{\"at\":\"2026-09-04T01:40:20.179Z\"}", json);
    }

    /**
     * The moment is named, not moved. The column already holds UTC, so
     * labelling it must not shift the clock.
     */
    @Test
    void theStoredWallClockIsLabelledNotConverted() {
        String json = mapper.writeValueAsString(
                new Holder(LocalDateTime.of(2026, 9, 4, 23, 0, 0))
        );

        assertEquals("{\"at\":\"2026-09-04T23:00:00Z\"}", json);
    }

    /**
     * What clients sent before this existed. Still accepted, or every existing
     * form would start failing on a deploy that only meant to change output.
     */
    @Test
    void aBareTimestampIsStillAccepted() {
        Holder holder = mapper.readValue(
                "{\"at\":\"2026-09-04T01:40:20\"}",
                Holder.class
        );

        assertEquals(LocalDateTime.of(2026, 9, 4, 1, 40, 20), holder.at());
    }

    /**
     * A client sending back what this API gave it — a draft reloaded and saved
     * again — must not be rejected for repeating our own format.
     */
    @Test
    void aTimestampWeEmittedIsAcceptedBack() {
        Holder holder = mapper.readValue(
                "{\"at\":\"2026-09-04T01:40:20.179Z\"}",
                Holder.class
        );

        assertEquals(
                LocalDateTime.of(2026, 9, 4, 1, 40, 20, 179_000_000),
                holder.at()
        );
    }

    /**
     * A reporter in Phnom Penh naming 15:00 their time means 08:00 UTC, and
     * that is what the column has to hold — storing a literal 15:00 would put a
     * number in it nobody can interpret later.
     */
    @Test
    void anOffsetIsConvertedToUtcRatherThanTruncated() {
        Holder holder = mapper.readValue(
                "{\"at\":\"2026-09-04T15:00:00+07:00\"}",
                Holder.class
        );

        assertEquals(LocalDateTime.of(2026, 9, 4, 8, 0, 0), holder.at());
    }

    @Test
    void aBlankTimestampReadsAsAbsentRatherThanFailing() {
        Holder holder = mapper.readValue("{\"at\":\"\"}", Holder.class);

        assertNull(holder.at());
    }

    /** The same builder Spring Boot hands the customizer at startup. */
    private static JsonMapper mapper() {
        JsonMapper.Builder builder = JsonMapper.builder();
        new JsonTimeConfig().utcLocalDateTimeCustomizer().customize(builder);
        return builder.build();
    }

    private record Holder(LocalDateTime at) {
    }
}
