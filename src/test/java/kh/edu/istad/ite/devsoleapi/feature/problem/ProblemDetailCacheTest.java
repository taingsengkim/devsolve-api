package kh.edu.istad.ite.devsoleapi.feature.problem;

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

class ProblemDetailCacheTest {

    private static Method loadMethod() throws NoSuchMethodException {
        return ProblemDetailCache.class.getMethod(
                "load",
                UUID.class,
                Problem.class
        );
    }

    @Test
    void isKeyedByProblemIdAloneAndNotByTheRowItWasGiven() throws Exception {
        Cacheable cacheable = loadMethod().getAnnotation(Cacheable.class);

        assertNotNull(cacheable);
        // The row is a parameter so the cache cannot serve a problem the
        // caller was not allowed to load, but it must stay out of the key —
        // an entity in a key would defeat the cache on every request.
        assertEquals("#problemId", cacheable.key());
        assertTrue(Arrays.asList(cacheable.cacheNames())
                .contains(CacheNames.PROBLEM_DETAIL));
    }

    @Test
    void singleFlightsItsQuery() throws Exception {
        Method method = loadMethod();
        Cacheable cacheable = method.getAnnotation(Cacheable.class);

        assertTrue(cacheable.sync());
        assertEquals(1, cacheable.cacheNames().length);
        assertEquals("", cacheable.unless());
        assertNull(method.getAnnotation(CacheEvict.class));
        assertNull(method.getAnnotation(Caching.class));
    }
}
