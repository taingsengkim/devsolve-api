package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.dto.DisputeResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ResolveDisputeRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisputeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface DisputeService {

    Page<DisputeResponse> findForAdmin(
            DisputeStatus status,
            UUID programId,
            UUID reportId,
            boolean pendingOnly,
            Pageable pageable
    );

    DisputeResponse findById(UUID id);

    DisputeResponse resolve(UUID id, ResolveDisputeRequest request);
}
