package kh.edu.istad.ite.devsoleapi.config;

import kh.edu.istad.ite.devsoleapi.common.ratelimit.InMemoryRateLimitStore;
import kh.edu.istad.ite.devsoleapi.common.ratelimit.RateLimitStore;
import kh.edu.istad.ite.devsoleapi.common.ratelimit.RedisRateLimitStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Which {@link RateLimitStore} the guards get.
 *
 * <p>The two conditions are exact complements, so one store bean exists
 * whatever {@code app.redis.enabled} says and neither branch depends on bean
 * ordering. Enabled is the default because the fallback inside
 * {@link RedisRateLimitStore} makes it safe: a machine with no Redis running
 * behaves like one with Redis switched off, minus a warning in the log.
 *
 * <p>The connection itself is Boot's, configured under {@code spring.data.redis}
 * — there is nothing here worth overriding in code.
 */
@Configuration
public class RedisConfig {

    @Bean
    @ConditionalOnProperty(
            name = "app.redis.enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    RateLimitStore redisRateLimitStore(StringRedisTemplate stringRedisTemplate) {
        return new RedisRateLimitStore(
                stringRedisTemplate,
                new InMemoryRateLimitStore()
        );
    }

    @Bean
    @ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false")
    RateLimitStore inMemoryRateLimitStore() {
        return new InMemoryRateLimitStore();
    }
}
