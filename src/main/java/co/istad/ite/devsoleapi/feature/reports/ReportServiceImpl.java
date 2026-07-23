package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.ReportMapper;
import co.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import co.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.UpdateDisclosureStateRequest;
import co.istad.ite.devsoleapi.feature.reports.entities.Report;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ReportMapper reportMapper;

    @Override
    public ReportResponse createNew(UUID programId, CreateReportRequest request) {

        Report report = reportMapper.toEntity(request);

        report.setProgramId(programId);

        Report savedReport = reportRepository.save(report);

        return reportMapper.toResponse(savedReport);
    }

    @Override
    public ReportResponse findById(UUID id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Report not found"));

        return reportMapper.toResponse(report);
    }

    @Override
    public List<ReportResponse> findAll(UUID programId) {
        List<Report> reports;
        if (programId != null) {
            reports = reportRepository.findByProgramId(programId);
        } else {
            reports = reportRepository.findAll();
        }
        return reportMapper.toResponse(reports);
    }

    @Override
    public List<ReportResponse> findAll() {
        return findAll(null);
    }

    @Override
    public ReportResponse triage(UUID id, TriageReportRequest request) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Report not found"));

        if (request != null && request.getState() != null) {
            report.setState(request.getState());
            reportRepository.save(report);
        }

        return reportMapper.toResponse(report);
    }

    @Override
    public ReportResponse updateDisclosureState(UUID id, UpdateDisclosureStateRequest request) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Report not found"));

        if (request != null && request.getDisclosureStatus() != null) {
            report.setDisclosureStatus(request.getDisclosureStatus());
            reportRepository.save(report);
        }

        return reportMapper.toResponse(report);
    }
}
