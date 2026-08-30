package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import java.util.List;

/**
 * One cached page of the showcase listing, without comment counts — those are
 * applied fresh per request. {@code PageImpl} cannot be cached directly, so the
 * service rebuilds the {@code Page} from this.
 */
public record ShowcaseListingSlice(
        List<ShowCasesSummaryResponse> content,
        long totalElements
) {
}
