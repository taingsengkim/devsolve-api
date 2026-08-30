package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A problem response carries the viewer's vote, bookmark and permissions, so
 * the usual staleness questions are joined by a sharper one: nothing
 * viewer-specific may reach a shared cache.
 */
class ProblemListingCacheTest {

    private static final List<String> FILTERS = List.of(
            "categoryId",
            "sdlcPhase",
            "tagSlug",
            "technologyName",
            "queryPattern"
    );

    private final SpelExpressionParser parser = new SpelExpressionParser();

    private static Method listingMethod() {
        return Arrays.stream(ProblemListingCache.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("load"))
                .findFirst()
                .orElseThrow();
    }

    private Cacheable loadAnnotation() {
        return listingMethod().getAnnotation(Cacheable.class);
    }

    private boolean cachedWith(
            String filter,
            Object value,
            boolean unansweredOnly,
            int page
    ) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        FILTERS.forEach(name -> context.setVariable(name, null));
        context.setVariable("unansweredOnly", unansweredOnly);
        context.setVariable("page", page);
        if (filter != null) {
            context.setVariable(filter, value);
        }

        return Boolean.TRUE.equals(parser
                .parseExpression(loadAnnotation().condition())
                .getValue(context, Boolean.class));
    }

    @Test
    void cachesTheUnfilteredFirstPage() {
        assertTrue(cachedWith(null, null, false, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "categoryId", "sdlcPhase", "tagSlug", "technologyName",
            "queryPattern"
    })
    void doesNotCacheAnyFilteredFeed(String filter) {
        assertFalse(
                cachedWith(filter, "set", false, 0),
                filter + " is filtered but the page would still be cached"
        );
    }

    @Test
    void doesNotCacheTheUnansweredFeed() {
        assertFalse(cachedWith(null, null, true, 0));
    }

    @Test
    void theConditionNamesEveryFilterTheFeedAccepts() {
        String condition = loadAnnotation().condition();
        for (String filter : FILTERS) {
            assertTrue(
                    condition.contains("#" + filter),
                    filter + " is not named in the condition"
            );
        }
        assertTrue(condition.contains("#unansweredOnly"));
    }

    @Test
    void doesNotCacheBeyondTheCachedPageBound() {
        assertTrue(cachedWith(null, null, false,
                ProblemListingCache.CACHED_PAGES - 1));
        assertFalse(cachedWith(null, null, false,
                ProblemListingCache.CACHED_PAGES));
    }

    /**
     * Only the score-ordered sorts are fixed by the query; the rest take their
     * order from the pageable. Keying on the sort alone would serve one
     * ordering's page as another's.
     */
    @Test
    void keyesByTheResolvedOrderingRatherThanTheSortAlone() {
        Cacheable cacheable = loadAnnotation();

        assertNotNull(cacheable);
        assertEquals(
                "#ordering + ':' + #status + ':' + #page + ':' + #size",
                cacheable.key()
        );
        assertTrue(Arrays.asList(cacheable.cacheNames())
                .contains(CacheNames.PROBLEM_LISTING));
    }

    @Test
    void theListingLoaderSingleFlightsItsQuery() {
        Method method = listingMethod();
        Cacheable cacheable = method.getAnnotation(Cacheable.class);

        assertTrue(cacheable.sync());
        assertEquals(1, cacheable.cacheNames().length);
        assertEquals("", cacheable.unless());
        assertNull(method.getAnnotation(CacheEvict.class));
        assertNull(method.getAnnotation(Caching.class));
    }

    /**
     * The enricher is what knows who is asking. A cache bean that took one as
     * a dependency could only be using it to bake viewer state into a shared
     * entry, so it must not have one.
     */
    @Test
    void neitherCacheCanReachTheEnricherThatKnowsTheViewer() {
        for (Class<?> bean : List.of(
                ProblemListingCache.class,
                ProblemDetailCache.class
        )) {
            assertTrue(
                    Arrays.stream(bean.getDeclaredFields())
                            .noneMatch(field -> field.getType()
                                    .equals(ProblemResponseEnricher.class)),
                    bean.getSimpleName() + " can reach the enricher, which "
                            + "would put one viewer's state in a shared cache"
            );
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "update", "submit", "moderate", "softDelete",
            "uploadAttachment", "removeAttachment"
    })
    void everyProblemWriteEvictsTheFeedAndTheDetail(String methodName) {
        Method method = Arrays.stream(
                        ProblemServiceImpl.class.getDeclaredMethods()
                )
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No " + methodName + " on ProblemServiceImpl"
                ));

        assertNotNull(
                evictsOn(method, CacheNames.PROBLEM_DETAIL)
                        .findFirst()
                        .orElse(null),
                methodName + " does not evict the cached problem detail"
        );
        assertTrue(
                evictsOn(method, CacheNames.PROBLEM_LISTING)
                        .anyMatch(CacheEvict::allEntries),
                methodName + " does not evict the problem feed"
        );
    }

    /**
     * Counting a view runs on the read path, so evicting there would empty the
     * feed on every visit. The view count going stale is the accepted trade.
     */
    @Test
    void countingAViewDoesNotEvictAnything() {
        Method method = Arrays.stream(
                        ProblemServiceImpl.class.getDeclaredMethods()
                )
                .filter(candidate ->
                        candidate.getName().equals("incrementViewCount"))
                .findFirst()
                .orElseThrow();

        assertNull(method.getAnnotation(CacheEvict.class));
        assertNull(method.getAnnotation(Caching.class));
    }

    private static Stream<CacheEvict> evictsOn(
            Method method,
            String cacheName
    ) {
        Caching caching = method.getAnnotation(Caching.class);
        Stream<CacheEvict> evicts = caching == null
                ? Stream.ofNullable(method.getAnnotation(CacheEvict.class))
                : Arrays.stream(caching.evict());

        return evicts.filter(evict -> Arrays.asList(evict.cacheNames())
                .contains(cacheName));
    }
}
