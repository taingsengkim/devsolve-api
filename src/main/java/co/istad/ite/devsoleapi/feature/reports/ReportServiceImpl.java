package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.config.security.AuthUtils;
import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.ReportMapper;
import co.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import co.istad.ite.devsoleapi.feature.reports.dto.RewardReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.UpdateDisclosureStateRequest;
import co.istad.ite.devsoleapi.feature.reports.entities.Report;
import co.istad.ite.devsoleapi.feature.reports.entities.ReportReward;
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
    private final ReportRewardRepository reportRewardRepository;
    private final ReportMapper reportMapper;

    @Override
    public ReportResponse createNew(UUID programId, CreateReportRequest request) {

        Report report = reportMapper.toEntity(request);

        report.setProgramId(programId);

        if (report.getReporterId() == null) {
            try {
                report.setReporterId(AuthUtils.extractUserId());
            } catch (Exception e) {
                report.setReporterId("system");
            }
        }

        if (report.getReportedSeverity() == null && request.getSeverity() != null) {
            report.setReportedSeverity(request.getSeverity());
        }

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
    public List<ReportResponse> findMine() {
        String reporterId = AuthUtils.extractUserId();
        List<Report> reports = reportRepository.findByReporterId(reporterId);
        return reportMapper.toResponse(reports);
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

    @Override
    public ReportResponse setReward(UUID id, RewardReportRequest request) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Report not found"));

        String awardedBy;
        try {
            awardedBy = AuthUtils.extractUserId();
        } catch (Exception e) {
            awardedBy = "system";
        }

        ReportReward reward = ReportReward.builder()
                .report(report)
                .amount(request.getAmount())
                .awardedBy(awardedBy)
                .note(request.getNote())
                .build();

        reportRewardRepository.save(reward);

        return reportMapper.toResponse(report);
    }
}
