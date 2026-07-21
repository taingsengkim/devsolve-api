package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import co.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;

import java.util.UUID;

public interface ReportService {
    ReportResponse createNew(UUID programId, CreateReportRequest request);

    ReportResponse findById(UUID id);

    ReportResponse triage(UUID id, TriageReportRequest request);
}
