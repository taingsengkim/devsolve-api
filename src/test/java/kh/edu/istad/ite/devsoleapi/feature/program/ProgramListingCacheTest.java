package kh.edu.istad.ite.devsoleapi.feature.program;

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
 * The condition carries the whole design: eleven filters, any one of which
 * makes a request its own key, read once and never hit again. Evaluating it
 * directly beats trusting eleven clauses by eye.
 */
class ProgramListingCacheTest {

    private static final List<String> FILTERS = List.of(
            "organizationId",
            "engagementType",
            "offersBounties",
            "queryPattern",
            "minimumBounty",
            "maximumBounty",
            "assetType",
            "maxSeverity",
            "industry",
            "country"
    );

    private final SpelExpressionParser parser = new SpelExpressionParser();

    private static Method listingMethod(String methodName) {
        return Arrays.stream(ProgramListingCache.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
    }

    private Cacheable loadAnnotation() {
        return listingMethod("load").getAnnotation(Cacheable.class);
    }

    /** Every filter null and a real page, then whichever filter is under test. */
    private boolean cachedWith(String filter, Object value, int page, int size) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        FILTERS.forEach(name -> context.setVariable(name, null));
        context.setVariable("page", page);
        context.setVariable("size", size);
        if (filter != null) {
            context.setVariable(filter, value);
        }

        return Boolean.TRUE.equals(parser
                .parseExpression(loadAnnotation().condition())
                .getValue(context, Boolean.class));
    }

    @Test
    void cachesTheUnfilteredFirstPage() {
        assertTrue(cachedWith(null, null, 0, 20));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "organizationId", "engagementType", "offersBounties",
            "queryPattern", "minimumBounty", "maximumBounty",
            "assetType", "maxSeverity", "industry", "country"
    })
    void doesNotCacheAnyFilteredListing(String filter) {
        assertFalse(
                cachedWith(filter, "set", 0, 20),
                filter + " is filtered but the page would still be cached"
        );
    }

    @Test
    void theConditionNamesEveryFilterTheListingAccepts() {
        // A filter missing from the condition is the quiet failure: that page
        // gets cached under a key that ignores it and served to everyone.
        String condition = loadAnnotation().condition();
        for (String filter : FILTERS) {
            assertTrue(
                    condition.contains("#" + filter),
                    filter + " is not named in the condition"
            );
        }
    }

    @Test
    void doesNotCacheBeyondTheCachedPageBound() {
        assertTrue(cachedWith(null, null,
                ProgramListingCache.CACHED_PAGES - 1, 20));
        assertFalse(cachedWith(null, null,
                ProgramListingCache.CACHED_PAGES, 20));
    }

    @Test
    void doesNotCacheAnUnpagedListing() {
        // Unpaged returns every public program; a size of zero is how the
        // caller says so, and holding that is not a page-sized thing to do.
        assertFalse(cachedWith(null, null, 0, 0));
    }

    @Test
    void keyesByOrderingSoTwoSortsDoNotShareAPage() {
        Cacheable cacheable = loadAnnotation();

        assertNotNull(cacheable);
        assertEquals(
                "#sortProperty + ':' + #sortDirection + ':' + #page + ':' + #size",
                cacheable.key()
        );
        assertTrue(Arrays.asList(cacheable.cacheNames())
                .contains(CacheNames.PROGRAM_LISTING));
    }

    /**
     * What Spring requires of a synchronized cacheable, checked on the first
     * call rather than at startup — so getting it wrong ships, and surfaces as
     * an IllegalStateException on a listing request.
     */
    @Test
    void theListingLoaderSingleFlightsItsQuery() {
        Method method = listingMethod("load");
        Cacheable cacheable = method.getAnnotation(Cacheable.class);

        assertTrue(cacheable.sync());
        assertEquals(1, cacheable.cacheNames().length);
        assertEquals("", cacheable.unless());
        assertNull(method.getAnnotation(CacheEvict.class));
        assertNull(method.getAnnotation(Caching.class));
    }

    /**
     * A write that loses its evict does not fail anything — the listing and the
     * detail just keep serving the program as it was until the TTL expires.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "updateProgram", "submitProgram", "publishProgram", "pauseProgram",
            "resumeProgram", "closeProgram", "deleteProgram",
            "removeProgramByAdmin", "restoreProgram", "approveProgram",
            "rejectProgram"
    })
    void everyProgramWriteEvictsTheListingAndTheDetail(String methodName) {
        Method method = Arrays.stream(
                        ProgramServiceImpl.class.getDeclaredMethods()
                )
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No " + methodName + " on ProgramServiceImpl"
                ));

        CacheEvict detail = evictsOn(method, CacheNames.PROGRAM_DETAIL)
                .findFirst()
                .orElse(null);
        assertNotNull(
                detail,
                methodName + " does not evict the cached program detail"
        );
        assertEquals("#id", detail.key());

        assertTrue(
                evictsOn(method, CacheNames.PROGRAM_LISTING)
                        .anyMatch(CacheEvict::allEntries),
                methodName + " does not evict the program listing"
        );
    }

    /** The evict may be declared directly or nested in a {@code @Caching}. */
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

    /**
     * Counting a view must not evict: it runs on the read path, so evicting
     * there would empty the listing on every visit.
     */
    @Test
    void countingAViewDoesNotEvictAnything() {
        Method method = Arrays.stream(
                        ProgramServiceImpl.class.getDeclaredMethods()
                )
                .filter(candidate ->
                        candidate.getName().equals("incrementViewCount"))
                .findFirst()
                .orElseThrow();

        assertNull(method.getAnnotation(CacheEvict.class));
        assertNull(method.getAnnotation(Caching.class));
    }
}
