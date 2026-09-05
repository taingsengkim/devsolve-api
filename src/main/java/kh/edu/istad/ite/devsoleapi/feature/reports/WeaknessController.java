package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.dto.WeaknessResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.WeaknessUsageResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The catalog a reporter picks a vulnerability class from. Read-only: adding
 * to it is {@code /api/v1/admin/weaknesses}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/weaknesses")
public class WeaknessController {

    private final WeaknessService weaknessService;

    /**
     * @param search matched against the CWE identifier and the name, so a
     *               submission form can drive a type-ahead rather than render
     *               the whole catalog in one select. Omitted, it returns
     *               everything active.
     */
    @GetMapping
    public Page<WeaknessResponse> findActive(
            @RequestParam(required = false) String search,
            @PageableDefault(
                    size = 20,
                    sort = "name",
                    direction = Sort.Direction.ASC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return weaknessService.findActive(search, pageable);
    }

    /**
     * The classes this platform actually receives, most reported first.
     *
     * <p>Ahead of {@code /{id}} in this file for the same reason it has to be:
     * "popular" would otherwise be read as a UUID and fail parsing before it
     * ever reached here.
     *
     * <p>Ordered by report count, so the sort on this one is not negotiable and
     * a {@code sort} parameter is refused rather than ignored.
     */
    @GetMapping("/popular")
    public List<WeaknessUsageResponse> findPopular(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return weaknessService.findPopular(limit);
    }

    @GetMapping("/{id}")
    public WeaknessResponse findById(@PathVariable UUID id) {
        return weaknessService.findById(id);
    }
}
