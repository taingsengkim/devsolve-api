package kh.edu.istad.ite.devsoleapi.config;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.common.cache.LoggingCacheErrorHandler;
import kh.edu.istad.ite.devsoleapi.feature.category.dto.CategoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.DuplicateJudgements;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.CachedProblem;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemListingSlice;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramListingSlice;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.PublicProgramResponseDto;
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

    /**
     * The default, and what every cache here uses unless it has a reason not
     * to.
     *
     * <p>Longer was tempting and mostly illusory: the listings are evicted
     * whole on any write that changes them, so on a moderated feed they never
     * reach a long TTL anyway. What a long TTL does reliably is widen the
     * blast radius of the one thing a TTL exists to catch — a write path added
     * later without an evict. Five minutes keeps that a glitch rather than a
     * day of a frozen feed, and a cache earns most of its keep in the first
     * minutes regardless.
     */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /**
     * Shorter than the rest: vote- and view-ordered pages are the one thing no
     * write can evict, because the counts they sort by move on reads.
     */
    private static final Duration SHOWCASE_LISTING_RANKED_TTL =
            Duration.ofSeconds(60);

    /**
     * Longer than everything else, because this one is not guarding against
     * staleness — its key already covers the draft and every candidate the
     * verdict was formed against, so an entry cannot go stale, only unused.
     * What the TTL bounds is Redis, and what the length buys is the case the
     * cache exists for: somebody editing a draft over half an hour and
     * rechecking it, without paying for the same judgement twice.
     */
    private static final Duration PROBLEM_DUPLICATE_REVIEW_TTL =
            Duration.ofHours(1);

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
                                    .entryTtl(DEFAULT_TTL)
                                    .serializeValuesWith(categoryListSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.LEADERBOARD,
                            defaults
                                    .entryTtl(DEFAULT_TTL)
                                    .serializeValuesWith(leaderboardSliceSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.SHOWCASE_DETAIL,
                            defaults
                                    .entryTtl(DEFAULT_TTL)
                                    .serializeValuesWith(showcaseDetailSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.SHOWCASE_LISTING,
                            defaults
                                    .entryTtl(DEFAULT_TTL)
                                    .serializeValuesWith(showcaseListingSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.SHOWCASE_LISTING_RANKED,
                            defaults
                                    .entryTtl(SHOWCASE_LISTING_RANKED_TTL)
                                    .serializeValuesWith(showcaseListingSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.PROGRAM_LISTING,
                            defaults
                                    .entryTtl(DEFAULT_TTL)
                                    .serializeValuesWith(programListingSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.PROGRAM_DETAIL,
                            defaults
                                    .entryTtl(DEFAULT_TTL)
                                    .serializeValuesWith(programDetailSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.PROBLEM_LISTING,
                            defaults
                                    .entryTtl(DEFAULT_TTL)
                                    .serializeValuesWith(problemListingSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.PROBLEM_DETAIL,
                            defaults
                                    .entryTtl(DEFAULT_TTL)
                                    .serializeValuesWith(problemDetailSerializer())
                    )
                    .withCacheConfiguration(
                            CacheNames.PROBLEM_DUPLICATE_REVIEW,
                            defaults
                                    .entryTtl(PROBLEM_DUPLICATE_REVIEW_TTL)
                                    .serializeValuesWith(
                                            duplicateReviewSerializer()
                                    )
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

    static SerializationPair<ProgramListingSlice> programListingSerializer() {
        return SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(
                        CACHE_MAPPER,
                        CACHE_MAPPER.constructType(ProgramListingSlice.class)
                )
        );
    }

    static SerializationPair<PublicProgramResponseDto> programDetailSerializer() {
        return SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(
                        CACHE_MAPPER,
                        CACHE_MAPPER.constructType(PublicProgramResponseDto.class)
                )
        );
    }

    static SerializationPair<ProblemListingSlice> problemListingSerializer() {
        return SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(
                        CACHE_MAPPER,
                        CACHE_MAPPER.constructType(ProblemListingSlice.class)
                )
        );
    }

    static SerializationPair<CachedProblem> problemDetailSerializer() {
        return SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(
                        CACHE_MAPPER,
                        CACHE_MAPPER.constructType(CachedProblem.class)
                )
        );
    }

    static SerializationPair<DuplicateJudgements> duplicateReviewSerializer() {
        return SerializationPair.fromSerializer(
                new JacksonJsonRedisSerializer<>(
                        CACHE_MAPPER,
                        CACHE_MAPPER.constructType(DuplicateJudgements.class)
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
