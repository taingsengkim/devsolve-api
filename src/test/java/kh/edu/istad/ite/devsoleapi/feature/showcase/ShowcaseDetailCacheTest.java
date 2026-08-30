package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowCaseStepServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The showcase detail cache is filled in one package and invalidated from two.
 * A step write that loses its {@code @CacheEvict} does not fail anything — it
 * just serves the old steps until the TTL expires, which is exactly the kind of
 * bug nobody reproduces.
 */
class ShowcaseDetailCacheTest {

    @Test
    void isKeyedByShowcaseId() throws NoSuchMethodException {
        Cacheable cacheable = ShowcaseDetailCache.class
                .getMethod("load", java.util.UUID.class)
                .getAnnotation(Cacheable.class);

        assertNotNull(cacheable);
        assertEquals("#showcaseId", cacheable.key());
        assertTrue(Arrays.asList(cacheable.cacheNames())
                .contains(CacheNames.SHOWCASE_DETAIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "create", "update", "uploadImage", "removeImage", "delete"
    })
    void everyStepWriteEvictsTheShowcaseItBelongsTo(String methodName) {
        Method method = Arrays.stream(
                        ShowCaseStepServiceImpl.class.getDeclaredMethods()
                )
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No " + methodName + " on ShowCaseStepServiceImpl"
                ));

        CacheEvict evict = method.getAnnotation(CacheEvict.class);

        assertNotNull(
                evict,
                methodName + " changes a showcase's steps but does not evict "
                        + "the cached detail"
        );
        assertEquals("#showcaseId", evict.key());
        assertTrue(Arrays.asList(evict.cacheNames())
                .contains(CacheNames.SHOWCASE_DETAIL));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "update", "updateStatus", "cancelRevision", "hardDelete"
    })
    void everyShowcaseWriteThatMovesTagsOrStepsEvicts(String methodName) {
        Method method = Arrays.stream(
                        ShowCasesServiceImpl.class.getDeclaredMethods()
                )
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No " + methodName + " on ShowCasesServiceImpl"
                ));

        CacheEvict evict = detailEvictOn(method);

        assertNotNull(evict, methodName + " does not evict the cached detail");
        assertEquals("#showcaseId", evict.key());
    }

    /**
     * A method may carry its {@code @CacheEvict} directly or nested in a
     * {@code @Caching} alongside evicts of other caches, and only the one
     * naming {@link CacheNames#SHOWCASE_DETAIL} is the one under test.
     */
    private static CacheEvict detailEvictOn(Method method) {
        Caching caching = method.getAnnotation(Caching.class);
        Stream<CacheEvict> evicts = caching == null
                ? Stream.ofNullable(method.getAnnotation(CacheEvict.class))
                : Arrays.stream(caching.evict());

        return evicts
                .filter(evict -> Arrays.asList(evict.cacheNames())
                        .contains(CacheNames.SHOWCASE_DETAIL))
                .findFirst()
                .orElse(null);
    }
}
