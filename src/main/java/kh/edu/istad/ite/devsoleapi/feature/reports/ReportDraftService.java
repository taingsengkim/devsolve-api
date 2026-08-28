package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportDraftResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.SaveReportDraftRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ReportDraftService {

    ReportDraftResponse create(UUID programId, SaveReportDraftRequest request);

    Page<ReportDraftResponse> findMine(UUID programId, Pageable pageable);

    ReportDraftResponse findById(UUID id);

    ReportDraftResponse save(UUID id, SaveReportDraftRequest request);

    void delete(UUID id);

    /**
     * Turns the draft into a real report and discards it. Fails as a whole:
     * if the report is rejected the draft is still there to fix.
     */
    ReportResponse submit(UUID id);
}
