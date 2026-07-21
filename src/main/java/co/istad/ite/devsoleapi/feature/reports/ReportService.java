package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportResponse;

import java.util.UUID;

public interface ReportService {
    CreateReportResponse createNew(UUID programId,CreateReportRequest request);
}
