package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The detail cache is keyed by program id while the API exposes a program by
 * id and by handle. Keying by handle as well would put the same program in two
 * entries, and a write would evict one of them.
 */
class ProgramDetailCacheTest {

    private static Method loadMethod() throws NoSuchMethodException {
        return ProgramDetailCache.class.getMethod("load", UUID.class);
    }

    @Test
    void isKeyedByProgramId() throws NoSuchMethodException {
        Cacheable cacheable = loadMethod().getAnnotation(Cacheable.class);

        assertNotNull(cacheable);
        assertEquals("#programId", cacheable.key());
        assertTrue(Arrays.asList(cacheable.cacheNames())
                .contains(CacheNames.PROGRAM_DETAIL));
    }

    @Test
    void singleFlightsItsQuery() throws NoSuchMethodException {
        Method method = loadMethod();
        Cacheable cacheable = method.getAnnotation(Cacheable.class);

        assertTrue(cacheable.sync());
        assertEquals(1, cacheable.cacheNames().length);
        assertEquals("", cacheable.unless());
        assertNull(method.getAnnotation(CacheEvict.class));
        assertNull(method.getAnnotation(Caching.class));
    }

    /**
     * The handle lookup must resolve to an id and read the same entry. A
     * second {@code @Cacheable} keyed by handle is the mistake this catches:
     * it would cache correctly and then go stale on the next write.
     */
    @Test
    void theHandleLookupDoesNotGetItsOwnCachedEntry() {
        boolean cachedByHandle = Arrays.stream(
                        ProgramDetailCache.class.getDeclaredMethods()
                )
                .map(method -> method.getAnnotation(Cacheable.class))
                .filter(java.util.Objects::nonNull)
                .anyMatch(cacheable -> cacheable.key().contains("andle"));

        assertTrue(
                !cachedByHandle,
                "a program cached by handle as well as by id has two entries "
                        + "and a write evicts only one"
        );
    }
}
