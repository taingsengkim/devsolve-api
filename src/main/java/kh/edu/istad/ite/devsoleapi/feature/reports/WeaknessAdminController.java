package kh.edu.istad.ite.devsoleapi.feature.reports;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateWeaknessRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.UpdateWeaknessRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.WeaknessResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Curation of the weakness catalog. Sits under {@code /api/v1/admin}, which
 * the security configuration already restricts to the ADMIN realm role.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/weaknesses")
public class WeaknessAdminController {

    private final WeaknessService weaknessService;

    /**
     * @param activeOnly defaults to false, unlike the reporter-facing list —
     *                   an administrator has to be able to see a retired entry
     *                   in order to bring it back.
     */
    @GetMapping
    public Page<WeaknessResponse> findForAdmin(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @PageableDefault(
                    size = 20,
                    sort = "name",
                    direction = Sort.Direction.ASC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return weaknessService.findForAdmin(search, activeOnly, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WeaknessResponse create(
            @Valid @RequestBody CreateWeaknessRequest request
    ) {
        return weaknessService.create(request);
    }

    @PatchMapping("/{id}")
    public WeaknessResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateWeaknessRequest request
    ) {
        return weaknessService.update(id, request);
    }

    /**
     * Only for an entry nothing has ever been filed under. One that reports
     * point at is retired with {@code {"isActive": false}} instead.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        weaknessService.delete(id);
    }
}
