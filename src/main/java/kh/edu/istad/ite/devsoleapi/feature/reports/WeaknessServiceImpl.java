package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateWeaknessRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.SuggestedWeaknessResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.UpdateWeaknessRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.WeaknessMapper;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.WeaknessResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.WeaknessUsageResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The weakness catalog — the vulnerability class a report is filed under.
 *
 * <p>Deliberately a closed vocabulary rather than free text. Its whole value
 * is that it aggregates: "injection is a third of everything filed against
 * this program" is only answerable while every injection report carries the
 * same identifier. Let reporters type the category and the same class arrives
 * as "XSS", "xss" and "Cross site scripting", and the field stops answering
 * anything. Reporters describe the finding in their own words in the title
 * and the write-up, which is where prose belongs.
 *
 * <p>The escape hatch for a finding that fits nothing in the list is triage,
 * not the submission form: the reporter may leave it unset, and the triager —
 * who knows the taxonomy — assigns it. When the class is genuinely missing,
 * an administrator adds it here and it is available to everyone from then on.
 */
@Service
@RequiredArgsConstructor
public class WeaknessServiceImpl implements WeaknessService {

    private static final String ADMIN_ROLE = "ADMIN";

    /**
     * A picker showing "what people report most" is a shortcut, not a second
     * catalog. Past a handful of entries it stops being a shortcut, and the
     * full list is one request away at {@code /api/v1/weaknesses}.
     */
    private static final int MAX_POPULAR = 50;

    /** Excludes an entry nothing has ever been filed under. */
    private static final long REPORTED_AT_LEAST_ONCE = 1L;

    /**
     * The states that mean a triager read the report and agreed with it.
     *
     * <p>{@code RETESTING} counts: the finding is agreed and understood, and
     * what is outstanding is only whether the fix holds. {@code NEW} and
     * {@code TRIAGING} do not — nobody has ruled on them yet, and counting them
     * would credit a class for reports that may still be rejected.
     */
    private static final Set<ReportState> VALID_STATES = Set.of(
            ReportState.VALID_CONFIRMED,
            ReportState.RETESTING,
            ReportState.RESOLVED
    );

    private static final Set<String> WEAKNESS_SORT_PROPERTIES = Set.of(
            "id",
            "cweId",
            "name",
            "isActive",
            "createdAt"
    );

    /**
     * Accepts the identifier however an administrator happens to type it —
     * "CWE-79", "cwe 79", "79" — and rejects anything that is not a CWE at
     * all, so the column stays searchable by number.
     */
    private static final Pattern CWE_ID = Pattern.compile(
            "^(?:CWE[-\\s_]?)?(\\d{1,10})$",
            Pattern.CASE_INSENSITIVE
    );

    private final WeaknessRepository weaknessRepository;
    private final ReportRepository reportRepository;
    private final WeaknessMapper weaknessMapper;

    @Override
    public Page<WeaknessResponse> findActive(
            String search,
            Pageable pageable
    ) {
        return weaknessRepository
                .searchActive(likePattern(search), validated(pageable))
                .map(weaknessMapper::toResponse);
    }

    @Override
    public WeaknessResponse findById(UUID id) {
        return weaknessMapper.toResponse(findWeakness(id));
    }

    /**
     * The top of the catalog by volume, for a submission form that would rather
     * show a reporter the five classes this platform actually receives than a
     * scroll of thirty in alphabetical order.
     */
    @Override
    @Transactional(readOnly = true)
    public List<WeaknessUsageResponse> findPopular(int limit) {
        if (limit < 1 || limit > MAX_POPULAR) {
            throw badRequest(
                    "limit must be between 1 and " + MAX_POPULAR
            );
        }
        return toUsageResponses(weaknessRepository.findActiveUsage(
                VALID_STATES,
                REPORTED_AT_LEAST_ONCE,
                PageRequest.of(0, limit)
        ).getContent());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<WeaknessUsageResponse> findUsageForAdmin(
            boolean includeUnused,
            boolean activeOnly,
            Pageable pageable
    ) {
        requireAdmin();
        // Unsorted on purpose: the ordering is an aggregate the query owns, and
        // a caller's sort would be appended after it rather than replace it.
        Pageable unsorted = unsorted(pageable);
        long minReports = includeUnused ? 0L : REPORTED_AT_LEAST_ONCE;

        Page<WeaknessUsageProjection> page = activeOnly
                ? weaknessRepository.findActiveUsage(
                        VALID_STATES,
                        minReports,
                        unsorted
                )
                : weaknessRepository.findAllUsage(
                        VALID_STATES,
                        minReports,
                        unsorted
                );

        long classified = reportRepository.countByWeaknessIsNotNull();
        return page.map(row -> toUsageResponse(row, classified));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SuggestedWeaknessResponse> findSuggestedForAdmin(
            Pageable pageable
    ) {
        requireAdmin();
        Page<SuggestedWeaknessProjection> page = reportRepository
                .findSuggestedWeaknesses(unsorted(pageable));

        // One lookup for the page rather than one per suggestion. A name that
        // is already in the catalog is the interesting case here: it means
        // reporters are typing a class they could have picked, so the fix is
        // the picker rather than a new entry.
        List<String> normalized = page.getContent().stream()
                .map(SuggestedWeaknessProjection::getNormalized)
                .filter(Objects::nonNull)
                .toList();
        Set<String> inCatalog = normalized.isEmpty()
                ? Set.of()
                : Set.copyOf(weaknessRepository.findNamesInLowerCase(
                        normalized
                ));

        return page.map(row -> new SuggestedWeaknessResponse(
                row.getName(),
                row.getReportCount(),
                row.getNormalized() != null
                        && inCatalog.contains(row.getNormalized()),
                row.getFirstSuggestedAt(),
                row.getLastSuggestedAt()
        ));
    }

    private List<WeaknessUsageResponse> toUsageResponses(
            List<WeaknessUsageProjection> rows
    ) {
        if (rows.isEmpty()) {
            return List.of();
        }
        long classified = reportRepository.countByWeaknessIsNotNull();
        return rows.stream()
                .map(row -> toUsageResponse(row, classified))
                .toList();
    }

    private WeaknessUsageResponse toUsageResponse(
            WeaknessUsageProjection row,
            long classifiedReports
    ) {
        return new WeaknessUsageResponse(
                row.getId(),
                row.getCweId(),
                row.getName(),
                row.getIsActive(),
                row.getReportCount(),
                row.getValidCount(),
                share(row.getReportCount(), classifiedReports),
                row.getLastReportedAt()
        );
    }

    /**
     * Zero rather than a division by zero on a platform that has not classified
     * anything yet — which is also the honest answer: no share of nothing.
     */
    private double share(long reportCount, long classifiedReports) {
        if (classifiedReports <= 0) {
            return 0d;
        }
        return Math.round(
                reportCount * 1_000d / classifiedReports
        ) / 10d;
    }

    /**
     * Refuses a sort rather than dropping one.
     *
     * <p>These listings are ordered by a count that only exists inside their
     * query, so a caller's sort cannot be honoured — and a sort silently ignored
     * is worse than one refused, because the caller believes it took.
     */
    private Pageable unsorted(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            throw badRequest(
                    "This listing is ordered by how much each weakness is "
                            + "reported and cannot be re-sorted"
            );
        }
        // Still through the validator, which is what caps the page size.
        Pageable validated = PageableValidator.requireAllowedSort(
                pageable,
                Set.of()
        );
        return PageRequest.of(
                validated.getPageNumber(),
                validated.getPageSize()
        );
    }

    @Override
    public Page<WeaknessResponse> findForAdmin(
            String search,
            boolean activeOnly,
            Pageable pageable
    ) {
        requireAdmin();
        String pattern = likePattern(search);
        Pageable validated = validated(pageable);
        Page<Weakness> page = activeOnly
                ? weaknessRepository.searchActive(pattern, validated)
                : weaknessRepository.searchAll(pattern, validated);
        return page.map(weaknessMapper::toResponse);
    }

    @Override
    @Transactional
    public WeaknessResponse create(CreateWeaknessRequest request) {
        requireAdmin();

        String cweId = normalizeCweId(request.cweId());
        String name = request.name().trim();
        requireCweIdAvailable(cweId, null);
        requireNameAvailable(name, null);

        Weakness weakness = Weakness.builder()
                .cweId(cweId)
                .name(name)
                .description(trimToNull(request.description()))
                .isActive(
                        request.isActive() == null || request.isActive()
                )
                .build();

        return weaknessMapper.toResponse(
                weaknessRepository.saveAndFlush(weakness)
        );
    }

    @Override
    @Transactional
    public WeaknessResponse update(UUID id, UpdateWeaknessRequest request) {
        requireAdmin();
        Weakness weakness = findWeakness(id);

        if (request.cweId() != null) {
            String cweId = normalizeCweId(request.cweId());
            requireCweIdAvailable(cweId, id);
            weakness.setCweId(cweId);
        }
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw badRequest("Name must not be blank");
            }
            requireNameAvailable(name, id);
            weakness.setName(name);
        }
        if (request.description() != null) {
            weakness.setDescription(trimToNull(request.description()));
        }
        if (request.isActive() != null) {
            weakness.setIsActive(request.isActive());
        }

        return weaknessMapper.toResponse(
                weaknessRepository.saveAndFlush(weakness)
        );
    }

    /**
     * Hard delete, but only for an entry no report has ever been filed under —
     * one added by mistake, or superseded before anybody used it.
     *
     * <p>An entry reports point at is retired instead, by patching
     * {@code isActive} to false: that takes it out of every picker while the
     * reports already classified under it keep reading correctly. Deleting one
     * would either be refused by the foreign key or, worse, leave those reports
     * pointing at a class that no longer exists.
     */
    @Override
    @Transactional
    public void delete(UUID id) {
        requireAdmin();
        Weakness weakness = findWeakness(id);

        long reports = reportRepository.countByWeaknessId(id);
        if (reports > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This weakness classifies " + reports + " report"
                            + (reports == 1 ? "" : "s")
                            + " and cannot be deleted. Deactivate it instead "
                            + "by patching isActive to false, which hides it "
                            + "from new submissions and leaves those reports "
                            + "intact."
            );
        }

        try {
            weaknessRepository.delete(weakness);
            // Forced now so a report filed between the count above and this
            // line still fails here rather than at an unrelated flush later.
            weaknessRepository.flush();
        } catch (DataIntegrityViolationException nowInUse) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This weakness is now in use and can no longer be "
                            + "deleted. Deactivate it instead."
            );
        }
    }

    private Weakness findWeakness(UUID id) {
        return weaknessRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Weakness not found"
                ));
    }

    /**
     * A complete LIKE pattern, lower-cased, so the repository never has to
     * reason about a null search term. A blank search becomes {@code %} and
     * returns the whole catalog.
     */
    private String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return "%";
        }
        String escaped = search.trim()
                .toLowerCase()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private Pageable validated(Pageable pageable) {
        return PageableValidator.requireAllowedSort(
                pageable,
                WEAKNESS_SORT_PROPERTIES
        );
    }

    private String normalizeCweId(String rawCweId) {
        String trimmed = trimToNull(rawCweId);
        if (trimmed == null) {
            return null;
        }
        Matcher matcher = CWE_ID.matcher(trimmed);
        if (!matcher.matches()) {
            throw badRequest(
                    "CWE identifier must look like \"CWE-79\" or \"79\""
            );
        }
        return "CWE-" + Long.parseLong(matcher.group(1));
    }

    private void requireCweIdAvailable(String cweId, UUID excludedId) {
        if (cweId == null) {
            return;
        }
        boolean taken = excludedId == null
                ? weaknessRepository.existsByCweIdIgnoreCase(cweId)
                : weaknessRepository.existsByCweIdIgnoreCaseAndIdNot(
                        cweId,
                        excludedId
                );
        if (taken) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    cweId + " is already in the catalog"
            );
        }
    }

    private void requireNameAvailable(String name, UUID excludedId) {
        boolean taken = excludedId == null
                ? weaknessRepository.existsByNameIgnoreCase(name)
                : weaknessRepository.existsByNameIgnoreCaseAndIdNot(
                        name,
                        excludedId
                );
        if (taken) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A weakness named \"" + name + "\" is already in the "
                            + "catalog"
            );
        }
    }

    private void requireAdmin() {
        if (!AuthUtils.hasRole(ADMIN_ROLE)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Required realm role: " + ADMIN_ROLE
            );
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ResponseStatusException badRequest(String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
    }
}
