package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateWeaknessRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.UpdateWeaknessRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.WeaknessResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface WeaknessService {

    Page<WeaknessResponse> findActive(String search, Pageable pageable);

    WeaknessResponse findById(UUID id);

    Page<WeaknessResponse> findForAdmin(
            String search,
            boolean activeOnly,
            Pageable pageable
    );

    WeaknessResponse create(CreateWeaknessRequest request);

    WeaknessResponse update(UUID id, UpdateWeaknessRequest request);

    void delete(UUID id);
}
