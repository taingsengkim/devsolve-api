package kh.edu.istad.ite.devsoleapi.config;

import kh.edu.istad.ite.devsoleapi.common.ratelimit.InMemoryRateLimitStore;
import kh.edu.istad.ite.devsoleapi.common.ratelimit.RateLimitStore;
import kh.edu.istad.ite.devsoleapi.common.ratelimit.RedisRateLimitStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What {@code app.redis.enabled} actually switches. A mistake in the two
 * complementary {@code @ConditionalOnProperty} branches does not fail loudly —
 * it produces either no {@link RateLimitStore} bean or two. The enabled case
 * also builds its context with nothing listening, which is what a developer
 * starting the API on a laptop does.
 */
class RedisWiringTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            DataRedisAutoConfiguration.class,
                            CacheAutoConfiguration.class
                    ))
                    .withUserConfiguration(RedisConfig.class, CacheConfig.class);

    @Test
    void usesRedisAndCachesByDefault() {
        contextRunner
                // Deliberately somewhere nothing is listening: building the
                // context must not require a reachable server.
                .withPropertyValues(
                        "spring.data.redis.host=192.0.2.1",
                        "spring.cache.type=redis"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RateLimitStore.class);
                    assertThat(context.getBean(RateLimitStore.class))
                            .isInstanceOf(RedisRateLimitStore.class);
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context.getBean(CacheManager.class))
                            .isInstanceOf(RedisCacheManager.class);
                });
    }

    @Test
    void switchesToInMemoryCountersAndNoCacheWhenDisabled() {
        contextRunner
                .withPropertyValues("app.redis.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RateLimitStore.class);
                    assertThat(context.getBean(RateLimitStore.class))
                            .isInstanceOf(InMemoryRateLimitStore.class);
                    // No @EnableCaching, so @Cacheable is a plain method call.
                    assertThat(context).doesNotHaveBean(CacheManager.class);
                });
    }

    @Test
    void staysOnRedisWhenTheFlagIsSetExplicitly() {
        contextRunner
                .withPropertyValues(
                        "app.redis.enabled=true",
                        "spring.data.redis.host=192.0.2.1"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RateLimitStore.class);
                    assertThat(context.getBean(RateLimitStore.class))
                            .isInstanceOf(RedisRateLimitStore.class);
                });
    }
}
