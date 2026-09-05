package kh.edu.istad.ite.devsoleapi.feature.program.dto;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.dto.ProgramAssetResponseDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One program on the public listing.
 *
 * @param inScopeAssets  every in-scope target in full — what it is, where it
 *                       is, and how far a finding against it can be rated
 * @param scope          the distinct asset types of {@link #inScopeAssets},
 *                       as the same names the {@code assetType} filter takes.
 *                       A card has room for "API, URL, MOBILE_APP" and not for
 *                       forty hostnames, and building that list client-side
 *                       from a page of programs is the sort of thing every
 *                       client would then do slightly differently
 * @param topSeverity    the highest severity any in-scope target admits, which
 *                       is the real answer to "is this worth my weekend".
 *                       Null when no target declares one — the program has not
 *                       said, which is not the same as NONE
 * @param resolvedReports findings this program has fixed
 * @param avgTriageDays  mean calendar days from submission to a triage
 *                       decision, rounded. Null until something has been
 *                       triaged; zero would read as same-day rather than as no
 *                       data. Measured against the triage stamp, which a
 *                       re-triage overwrites, so a program that answers fast
 *                       and revisits late looks slower here than it was
 */
public record ProgramSummaryResponseDto(
        UUID id,
        UUID organizationId,
        String organizationName,
        ProgramOrganizationDto organization,
        String handle,
        String name,
        String description,
        EngagementType engagementType,
        Boolean offersBounties,
        BigDecimal minimumBounty,
        BigDecimal maximumBounty,
        List<ProgramAssetResponseDto> inScopeAssets,
        List<String> scope,
        Severity topSeverity,
        long viewCount,
        long followerCount,
        long totalSubmissions,
        long resolvedReports,
        Integer avgTriageDays,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
