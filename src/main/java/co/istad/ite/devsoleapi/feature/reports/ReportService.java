package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportResponse;

public interface ReportService {
    CreateReportResponse createNew(CreateReportRequest request);
}
