package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowRepository;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramListingSlice;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramSummaryResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAssetRepository;
import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The public program listing query, behind a cache. A separate bean because
 * {@code @Cacheable} is proxy-applied.
 *
 * <p>Everything the listing needs is loaded here rather than by the caller, so
 * a hit costs no queries at all: one page is five round trips otherwise — the
 * programs, their organizations, their in-scope assets, and an aggregate each
 * for follower and submission counts.
 */
@Component
@RequiredArgsConstructor
public class ProgramListingCache {

    /**
     * Only the first pages are cached, and only unfiltered ones. Repeated in
     * the {@code condition} below, which SpEL cannot read this field from;
     * {@code ProgramListingCacheTest} pins the two together.
     */
    public static final int CACHED_PAGES = 10;

    private final ProgramRepository programRepository;
    private final ProgramAssetRepository programAssetRepository;
    private final OrganizationRepository organizationRepository;
    private final FollowRepository followRepository;
    private final ReportRepository reportRepository;
    private final ProgramMapper mapper;

    /**
     * Cached only when nothing is filtered. There are eleven filters on this
     * listing, so a cached filtered page would be a key read once and never
     * hit again — the same trade as the showcase listing, with more ways to
     * miss. An unpaged request ({@code size} of zero) is not cached either: it
     * returns every public program, which is not a page-sized thing to hold.
     */
    @Cacheable(
            cacheNames = CacheNames.PROGRAM_LISTING,
            key = "#sortProperty + ':' + #sortDirection + ':' + #page + ':' + #size",
            condition = "#organizationId == null && #engagementType == null "
                    + "&& #offersBounties == null && #queryPattern == null "
                    + "&& #minimumBounty == null && #maximumBounty == null "
                    + "&& #assetType == null && #maxSeverity == null "
                    + "&& #industry == null && #country == null "
                    + "&& #size > 0 && #page < 10",
            sync = true
    )
    @Transactional(readOnly = true)
    public ProgramListingSlice load(
            UUID organizationId,
            String engagementType,
            Boolean offersBounties,
            String queryPattern,
            BigDecimal minimumBounty,
            BigDecimal maximumBounty,
            String assetType,
            String maxSeverity,
            String industry,
            String country,
            String sortProperty,
            String sortDirection,
            int page,
            int size
    ) {
        Page<Program> programs = programRepository.searchPublicPrograms(
                organizationId,
                engagementType,
                offersBounties,
                queryPattern,
                minimumBounty,
                maximumBounty,
                assetType,
                maxSeverity,
                industry,
                country,
                sortProperty,
                sortDirection,
                size > 0 ? PageRequest.of(page, size) : Pageable.unpaged()
        );

        PublicProgramContext context =
                loadPublicProgramContext(programs.getContent());

        List<ProgramSummaryResponseDto> content = programs.stream()
                .map(program -> mapper.toSummaryDto(
                        program,
                        context.organizations().get(program.getOrganizationId()),
                        context.assetsByProgram().getOrDefault(
                                program.getId(),
                                List.of()
                        ),
                        context.followerCounts().getOrDefault(program.getId(), 0L),
                        context.submissionCounts().getOrDefault(program.getId(), 0L)
                ))
                .toList();

        return new ProgramListingSlice(content, programs.getTotalElements());
    }

    private PublicProgramContext loadPublicProgramContext(
            Collection<Program> programs
    ) {
        if (programs.isEmpty()) {
            return new PublicProgramContext(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of()
            );
        }

        // The whole row, not just the name: the public listing now carries the
        // organization's profile, and this query already had to load it.
        Set<UUID> organizationIds = programs.stream()
                .map(Program::getOrganizationId)
                .collect(Collectors.toSet());
        Map<UUID, Organization> organizations = organizationRepository
                .findAllById(organizationIds)
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        Organization::getId,
                        organization -> organization
                ));

        Set<UUID> programIds = programs.stream()
                .map(Program::getId)
                .collect(Collectors.toSet());
        Map<UUID, List<ProgramAsset>> assetsByProgram = programAssetRepository
                .findInScopeByProgramIds(programIds)
                .stream()
                .collect(Collectors.groupingBy(
                        asset -> asset.getProgram().getId()
                ));
        Map<UUID, Long> followerCounts = toCountMap(
                followRepository.countByFollowableIds(
                        FollowType.PROGRAM,
                        programIds
                )
        );
        Map<UUID, Long> submissionCounts = toCountMap(
                reportRepository.countByProgramIds(programIds)
        );

        return new PublicProgramContext(
                organizations,
                assetsByProgram,
                followerCounts,
                submissionCounts
        );
    }

    private Map<UUID, Long> toCountMap(
            Collection<IdCountProjection> counts
    ) {
        return counts.stream().collect(Collectors.toUnmodifiableMap(
                IdCountProjection::getId,
                IdCountProjection::getTotal
        ));
    }

    private record PublicProgramContext(
            Map<UUID, Organization> organizations,
            Map<UUID, List<ProgramAsset>> assetsByProgram,
            Map<UUID, Long> followerCounts,
            Map<UUID, Long> submissionCounts
    ) {
    }
}
