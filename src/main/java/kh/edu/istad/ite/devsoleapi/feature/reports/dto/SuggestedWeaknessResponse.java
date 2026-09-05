package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import java.time.LocalDateTime;

/**
 * A class reporters named for themselves, with how many of them named it.
 *
 * <p>The working list for extending the catalog: a name several people reached
 * for and nothing in the catalog covers is the definition of a gap. Promoting
 * one is an ordinary {@code POST /api/v1/admin/weaknesses}.
 *
 * @param name      one of the spellings reporters actually used
 * @param inCatalog whether the catalog already has an entry by this name, which
 *                  means the gap is not in the vocabulary but in the picker —
 *                  people are typing a class they could have chosen, and the
 *                  fix is the search, not a new entry
 */
public record SuggestedWeaknessResponse(
        String name,
        long reportCount,
        boolean inCatalog,
        LocalDateTime firstSuggestedAt,
        LocalDateTime lastSuggestedAt
) {
}
