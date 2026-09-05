package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateWeaknessRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.SuggestedWeaknessResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.UpdateWeaknessRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.WeaknessResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.WeaknessUsageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface WeaknessService {

    Page<WeaknessResponse> findActive(String search, Pageable pageable);

    WeaknessResponse findById(UUID id);

    /**
     * The classes actually being reported, most first. Active entries only, and
     * only ones something has been filed under.
     */
    List<WeaknessUsageResponse> findPopular(int limit);

    /**
     * The whole catalog with its usage, for an administrator deciding what to
     * add, retire or leave alone.
     *
     * @param includeUnused entries nothing has ever been filed under. Off by
     *                      default, because the question is usually "what are we
     *                      receiving"; on, it is "is this catalog earning its
     *                      keep"
     */
    Page<WeaknessUsageResponse> findUsageForAdmin(
            boolean includeUnused,
            boolean activeOnly,
            Pageable pageable
    );

    /**
     * The classes reporters named themselves because the catalog had none that
     * fit — the platform's own record of where its vocabulary is short.
     */
    Page<SuggestedWeaknessResponse> findSuggestedForAdmin(Pageable pageable);

    Page<WeaknessResponse> findForAdmin(
            String search,
            boolean activeOnly,
            Pageable pageable
    );

    WeaknessResponse create(CreateWeaknessRequest request);

    WeaknessResponse update(UUID id, UpdateWeaknessRequest request);

    void delete(UUID id);
}
