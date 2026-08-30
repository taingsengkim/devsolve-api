package kh.edu.istad.ite.devsoleapi.config;

import kh.edu.istad.ite.devsoleapi.feature.category.CategoryScope;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardResponse;
import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardSlice;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseDetailParts;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseTagResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
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

    @Test
    void roundTripsALeaderboardSliceWithItsTotalIntact() {
        SerializationPair<LeaderboardSlice> leaderboard =
                CacheConfig.leaderboardSliceSerializer();

        LeaderboardSlice original = new LeaderboardSlice(
                List.of(new LeaderboardResponse(
                        7,
                        UUID.randomUUID(),
                        "Sok Dara",
                        "https://example.test/avatars/dara.png",
                        "KH",
                        420,
                        12,
                        9,
                        2,
                        3
                )),
                58
        );

        LeaderboardSlice restored =
                leaderboard.read(leaderboard.write(original));

        assertNotNull(restored);
        assertEquals(original, restored);
        // The total drives how many pages the caller thinks exist, so a lost
        // one is a silently truncated leaderboard rather than a visible error.
        assertEquals(58, restored.totalElements());
    }

    @Test
    void roundTripsShowcaseDetailPartsKeepingStepOrder() {
        SerializationPair<ShowcaseDetailParts> showcase =
                CacheConfig.showcaseDetailSerializer();

        ShowcaseDetailParts original = new ShowcaseDetailParts(
                List.of(new ShowcaseTagResponse(
                        UUID.randomUUID(),
                        "Reverse Engineering",
                        "reverse-engineering"
                )),
                List.of(
                        new ShowcaseStepResponse(
                                UUID.randomUUID(),
                                1,
                                "Recon",
                                "Map the surface",
                                "nmap -sV target",
                                "https://example.test/1.png",
                                null,
                                LocalDateTime.of(2026, 8, 30, 9, 0),
                                LocalDateTime.of(2026, 8, 30, 9, 30)
                        ),
                        new ShowcaseStepResponse(
                                UUID.randomUUID(),
                                2,
                                "Exploit",
                                null,
                                null,
                                null,
                                null,
                                LocalDateTime.of(2026, 8, 30, 10, 0),
                                null
                        )
                )
        );

        ShowcaseDetailParts restored =
                showcase.read(showcase.write(original));

        assertNotNull(restored);
        assertEquals(original, restored);
        // Steps are ordered by step number in the query, and the reader relies
        // on that order rather than re-sorting.
        assertEquals(
                List.of(1, 2),
                restored.steps().stream()
                        .map(ShowcaseStepResponse::stepNumber)
                        .toList()
        );
    }

    @Test
    void roundTripsShowcaseDetailPartsWithNoTagsOrSteps() {
        SerializationPair<ShowcaseDetailParts> showcase =
                CacheConfig.showcaseDetailSerializer();

        ShowcaseDetailParts bare =
                new ShowcaseDetailParts(List.of(), List.of());

        assertEquals(bare, showcase.read(showcase.write(bare)));
    }

    @Test
    void roundTripsALeaderboardSliceWithNoRows() {
        SerializationPair<LeaderboardSlice> leaderboard =
                CacheConfig.leaderboardSliceSerializer();

        LeaderboardSlice empty = new LeaderboardSlice(List.of(), 0);

        assertEquals(empty, leaderboard.read(leaderboard.write(empty)));
    }
}
