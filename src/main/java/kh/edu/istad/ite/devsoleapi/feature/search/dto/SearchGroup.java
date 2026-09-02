package kh.edu.istad.ite.devsoleapi.feature.search.dto;

import java.util.List;

/**
 * The hits from one index.
 *
 * @param totalHits how many that index holds for this query in total, not how
 *                  many are in {@code hits}. In the grouped mode that is the
 *                  number behind a "see all 43 programs" link.
 */
public record SearchGroup(
        String type,
        List<SearchHit> hits,
        long totalHits
) {
}
