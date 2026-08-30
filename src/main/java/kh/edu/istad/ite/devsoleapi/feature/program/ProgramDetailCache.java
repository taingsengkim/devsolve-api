package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowRepository;
import kh.edu.istad.ite.devsoleapi.feature.follow.FollowType;
import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.PublicProgramResponseDto;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * A whole public program response, cached per program.
 *
 * <p>A separate bean because {@code @Cacheable} is proxy-applied: annotating a
 * method the service calls on itself would cache nothing, silently.
 *
 * <p>Unlike the showcase detail cache this holds the counts as well as the
 * stable data. Splitting them would be the wrong way round here: the follower
 * and submission totals are the expensive reads — the latter an aggregate over
 * the whole report table — while the program row, its organization and its
 * assets are indexed lookups. Caching only the stable half would leave the
 * costly queries running on every request.
 *
 * <p>So the TTL bounds the counts, and every write that changes a program
 * evicts. The caller still resolves the program itself on each request, which
 * is what makes a pause, a close or a delete take effect immediately.
 */
@Component
@RequiredArgsConstructor
public class ProgramDetailCache {

    private final ProgramRepository programRepository;
    private final ProgramAssetRepository programAssetRepository;
    private final OrganizationRepository organizationRepository;
    private final FollowRepository followRepository;
    private final ProgramMapper mapper;

    @Cacheable(
            cacheNames = CacheNames.PROGRAM_DETAIL,
            key = "#programId",
            sync = true
    )
    @Transactional(readOnly = true)
    public PublicProgramResponseDto load(UUID programId) {
        Program program = programRepository.findById(programId)
                .orElseThrow(this::programNotFound);
        Organization organization = organizationRepository
                .findById(program.getOrganizationId())
                .orElseThrow(this::programNotFound);
        List<ProgramAsset> assets = programAssetRepository
                .findByProgramIdOrderByCreatedAtAsc(programId);
        ProgramRepository.PublicProgramStatistics statistics =
                programRepository.findPublicStatisticsByProgramId(programId);

        return mapper.toPublicResponseDto(
                program,
                organization,
                assets,
                statistics.getTotalResearchers(),
                statistics.getTotalSubmissions(),
                followRepository.countByFollowableTypeAndFollowableId(
                        FollowType.PROGRAM,
                        programId
                )
        );
    }

    private ResponseStatusException programNotFound() {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Program not found"
        );
    }
}
