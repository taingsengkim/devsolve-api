package co.istad.ite.devsoleapi.feature.reports;


import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportResponse;
import co.istad.ite.devsoleapi.feature.reports.dto.ReportMapper;
import co.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import co.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ReportMapper reportMapper;
    @Override
    public CreateReportResponse createNew(CreateReportRequest request) {

        Report report = reportMapper.toEntity(request);

        Report savedReport = reportRepository.save(report);

        return reportMapper.toCreateResponse(savedReport);
    }
}
