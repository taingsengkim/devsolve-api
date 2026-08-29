package kh.edu.istad.ite.devsoleapi.config;

import kh.edu.istad.ite.devsoleapi.feature.category.CategoryScope;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * With {@code app.redis.enabled=false} the suite never builds a cache, so a
 * serializer that cannot read back what it wrote would first show up as a
 * ClassCastException on a live cache hit. These round-trips stand in for that.
 */
class CacheConfigTest {

    private final SerializationPair<List<CategoryResponse>> serializer =
            CacheConfig.categoryListSerializer();

    @Test
    void roundTripsACategoryListWithEveryFieldIntact() {
        CategoryResponse original = new CategoryResponse(
                UUID.randomUUID(),
                "Web Security",
                "web-security",
                CategoryScope.PROBLEM,
                "Anything that speaks HTTP",
                "https://example.test/icons/web.png",
                3,
                true,
                LocalDateTime.of(2026, 8, 29, 10, 15, 30, 123_000_000),
                LocalDateTime.of(2026, 8, 29, 11, 0, 0)
        );

        List<CategoryResponse> restored =
                serializer.read(serializer.write(List.of(original)));

        assertNotNull(restored);
        assertEquals(1, restored.size());
        // Records compare by value, so this covers the UUID, the enum and both
        // LocalDateTimes — the types most likely to come back as something else.
        assertEquals(original, restored.getFirst());
    }

    @Test
    void roundTripsNullFieldsRatherThanDroppingThem() {
        CategoryResponse sparse = new CategoryResponse(
                UUID.randomUUID(),
                "Untitled",
                "untitled",
                CategoryScope.SHOWCASE,
                null,
                null,
                null,
                null,
                null,
                null
        );

        List<CategoryResponse> restored =
                serializer.read(serializer.write(List.of(sparse)));

        assertEquals(List.of(sparse), restored);
    }

    @Test
    void roundTripsAnEmptyList() {
        assertEquals(List.of(), serializer.read(serializer.write(List.of())));
    }
}
