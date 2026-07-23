package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import co.istad.ite.devsoleapi.feature.reports.dto.RewardReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.UpdateDisclosureStateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ReportController {
    private final ReportService reportService;

    @PostMapping("/programs/{programId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse createNew(
            @PathVariable UUID programId,
            @Valid @RequestBody CreateReportRequest request) {
        return reportService.createNew(programId, request);
    }

    @GetMapping("/programs/{programId}/reports")
    public List<ReportResponse> findByProgramId(@PathVariable UUID programId) {
        return reportService.findAll(programId);
    }

    @GetMapping("/reports")
    public List<ReportResponse> findAll(@RequestParam(required = false) UUID programId) {
        return reportService.findAll(programId);
    }

    @GetMapping("/reports/mine")
    public List<ReportResponse> findMine() {
        return reportService.findMine();
    }

    @GetMapping("/reports/{id}")
    public ReportResponse findById(@PathVariable UUID id) {
        return reportService.findById(id);
    }

    @PatchMapping("/reports/{id}/triage")
    public ReportResponse triage(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) TriageReportRequest request) {
        return reportService.triage(id, request);
    }

    @PatchMapping({ "/reports/{id}/disclosure", "/reports/{id}/disclosure-state" })
    public ReportResponse updateDisclosureState(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) UpdateDisclosureStateRequest request) {
        return reportService.updateDisclosureState(id, request);
    }

    @PatchMapping("/reports/{id}/reward")
    public ReportResponse setReward(
            @PathVariable UUID id,
            @Valid @RequestBody RewardReportRequest request) {
        return reportService.setReward(id, request);
    }
}
