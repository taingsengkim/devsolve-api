package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import co.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.UpdateDisclosureStateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
            @Valid @RequestBody CreateReportRequest request
    ) {
        return reportService.createNew(programId, request);
    }

    @GetMapping("/reports/{id}")
    public ReportResponse findById(@PathVariable UUID id) {
        return reportService.findById(id);
    }

    @PatchMapping("/reports/{id}/triage")
    public ReportResponse triage(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) TriageReportRequest request
    ) {
        return reportService.triage(id, request);
    }

    @PatchMapping({"/reports/{id}/disclosure", "/reports/{id}/disclosure-state"})
    public ReportResponse updateDisclosureState(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) UpdateDisclosureStateRequest request
    ) {
        return reportService.updateDisclosureState(id, request);
    }
}
