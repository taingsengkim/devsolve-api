package kh.edu.istad.ite.devsoleapi.feature.reputation;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaderboardCacheTest {

    private Cacheable loadAnnotation() throws NoSuchMethodException {
        return LeaderboardCache.class
                .getMethod("load", int.class, int.class)
                .getAnnotation(Cacheable.class);
    }

    /**
     * SpEL cannot read {@code CACHED_PAGES}, so the bound is written twice.
     * This fails if only one of them is changed.
     */
    @Test
    void theCachedPageBoundMatchesTheConditionItIsDocumenting()
            throws NoSuchMethodException {

        assertEquals(
                "#page < " + LeaderboardCache.CACHED_PAGES,
                loadAnnotation().condition()
        );
    }

    @Test
    void pagesAreKeyedBySizeTooSoASizeChangeIsNotAStaleHit()
            throws NoSuchMethodException {

        assertEquals("#page + ':' + #size", loadAnnotation().key());
    }
}
