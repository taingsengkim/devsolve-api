package co.istad.ite.devsoleapi.feature.reports;


import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportResponse;
import co.istad.ite.devsoleapi.feature.reports.dto.ReportMapper;
import co.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import co.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ReportMapper reportMapper;
    @Override
    public CreateReportResponse createNew(UUID programId, CreateReportRequest request) {

        Report report = reportMapper.toEntity(request);

        report.setProgramId(programId);

        Report savedReport = reportRepository.save(report);

        return reportMapper.toCreateResponse(savedReport);
    }

    @Override
    public CreateReportResponse findById(UUID id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Report not found"
                        )
                );

        return reportMapper.toCreateResponse(report);
    }
}
