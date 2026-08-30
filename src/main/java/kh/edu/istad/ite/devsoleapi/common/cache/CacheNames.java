package kh.edu.istad.ite.devsoleapi.common.cache;

/**
 * The names of the Redis caches.
 *
 * <p>Constants rather than string literals because a cache name is a contract
 * between the {@code @Cacheable} that fills it, the {@code @CacheEvict} that
 * clears it and the configuration that gives it a TTL — and misspelling it in
 * any one of them fails silently.
 */
public final class CacheNames {

    /**
     * Every category listing, keyed by which listing and which scope. Dropped
     * whole on any write: it holds a handful of entries, and a category edit
     * invalidates all of them anyway.
     */
    public static final String CATEGORIES = "categories";

    private CacheNames() {
    }
}
