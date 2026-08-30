package kh.edu.istad.ite.devsoleapi.config;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.common.cache.LoggingCacheErrorHandler;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardSlice;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseDetailParts;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseListingSlice;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/**
 * Redis-backed {@code @Cacheable}.
 *
 * <p>Switched off with the rest of Redis by {@code app.redis.enabled=false},
 * and switched off completely: without {@code @EnableCaching} there is no cache
 * interceptor, so every {@code @Cacheable} becomes a plain method call rather
 * than falling back to a heap cache nobody configured.
 *
 * <p>Values are serialized with an explicit type per cache. Jackson's default
 * typing — what the generic serializer relies on — only tags non-final types,
 * and these DTOs are records, so a cached {@code List<CategoryResponse>} would
 * write happily and read back as a list of {@code LinkedHashMap}.
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(
        name = "app.redis.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CacheConfig implements CachingConfigurer {

    /** The taxonomy changes rarely and every write path evicts, so this is only a backstop. */
    private static final Duration CATEGORY_TTL = Duration.ofDays(1);

    /** Nothing evicts the leaderboard, so this is the only thing bounding staleness. */
    private static final Duration LEADERBOARD_TTL = Duration.ofMinutes(5);

    /** Every step and tag write evicts; this only bounds a path someone forgets to. */
    private static final Duration SHOWCASE_DETAIL_TTL = Duration.ofDays(1);

    /** Moderation and deletion evict, and nothing else changes these orderings. */
    private static final Duration SHOWCASE_LISTING_TTL = Duration.ofDays(1);

    /** Votes and views move on reads, so nothing evicts these pages. */
    private static final Duration SHOWCASE_LISTING_RANKED_TTL =
            Duration.ofSeconds(60);

    /**
     * Deliberately not the HTTP {@code ObjectMapper}: cached bytes outlive the
     * request that wrote them, so their format should not move because someone
     * changed how responses are rendered.
     */
    private static final ObjectMapper CACHE_MAPPER = JsonMapper.builder().build();

    @Bean
    RedisCacheManagerBuilderCustomizer devsolveCacheCustomizer() {
        return builder -> {
            // Start from Boot's defaults so spring.cache.redis.* — the key
            // prefix above all — still applies.
            RedisCacheConfiguration defaults = builder.cacheDefaults();

            builder
                    // Evicts wait for commit. Without this an evict inside a
                    // @Transactional write can land first, letting a concurrent
                    // read repopulate from the pre-commit state.
                    .transactionAware()
                    .withCacheConfiguration(
                            CacheNames.CATEGORIES,
                            defaults
                                    .entryTtl(CATEGORY_TTL)
                                    .serializeValuesWith(categoryListSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.LEADERBOARD,
                            defaults
                                    .entryTtl(LEADERBOARD_TTL)
                                    .serializeValuesWith(leaderboardSliceSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.SHOWCASE_DETAIL,
                            defaults
                                    .entryTtl(SHOWCASE_DETAIL_TTL)
                                    .serializeValuesWith(showcaseDetailSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.SHOWCASE_LISTING,
                            defaults
                                    .entryTtl(SHOWCASE_LISTING_TTL)
                                    .serializeValuesWith(showcaseListingSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.SHOWCASE_LISTING_RANKED,
                            defaults
                                    .entryTtl(SHOWCASE_LISTING_RANKED_TTL)
                                    .serializeValuesWith(showcaseListingSerializer())
                    );
        };
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    /** Package-private so a test can round-trip a real payload through it. */
    static SerializationPair<List<CategoryResponse>> categoryListSerializer() {
        return SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(
                        CACHE_MAPPER,
                        CACHE_MAPPER.constructType(
                                new TypeReference<List<CategoryResponse>>() {
                                }
                        )
                )
        );
    }

    static SerializationPair<LeaderboardSlice> leaderboardSliceSerializer() {
        return SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(
                        CACHE_MAPPER,
                        CACHE_MAPPER.constructType(LeaderboardSlice.class)
                )
        );
    }

    static SerializationPair<ShowcaseListingSlice> showcaseListingSerializer() {
        return SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(
                        CACHE_MAPPER,
                        CACHE_MAPPER.constructType(ShowcaseListingSlice.class)
                )
        );
    }

    static SerializationPair<ShowcaseDetailParts> showcaseDetailSerializer() {
        return SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(
                        CACHE_MAPPER,
                        CACHE_MAPPER.constructType(ShowcaseDetailParts.class)
                )
        );
    }
}
