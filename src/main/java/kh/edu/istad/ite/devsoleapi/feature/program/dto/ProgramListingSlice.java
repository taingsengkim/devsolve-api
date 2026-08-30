package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import java.util.List;

/**
 * One cached page of the public program listing.
 *
 * @param totalElements carried alongside the rows because the caller rebuilds
 *                      the {@code Page} from this, and a lost total is a
 *                      silently truncated listing rather than a visible error.
 */
public record ProgramListingSlice(
        List<ProgramSummaryResponseDto> content,
        long totalElements
) {
}
