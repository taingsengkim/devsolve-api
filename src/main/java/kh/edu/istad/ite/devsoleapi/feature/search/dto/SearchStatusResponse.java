package kh.edu.istad.ite.devsoleapi.feature.search.dto;

import java.util.List;

/**
 * Whether search is switched on, whether it answers, and what it holds.
 *
 * @param enabled   what the configuration says.
 * @param reachable what Meilisearch says. The two differ exactly when
 *                  something is wrong, which is what makes this pair worth
 *                  reporting rather than a single flag.
 */
public record SearchStatusResponse(
        boolean enabled,
        boolean reachable,
        List<SearchIndexStatus> indexes
) {
}
