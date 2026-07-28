package kh.edu.istad.ite.devsoleapi.feature.reports;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RewardReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.UpdateDisclosureStateRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/programs/{programId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(
            @PathVariable UUID programId,
            @Valid @RequestBody CreateReportRequest request
    ) {
        return reportService.create(programId, request);
    }

    @GetMapping("/programs/{programId}/reports")
    public Page<ReportResponse> findByProgram(
            @PathVariable UUID programId,
            @RequestParam(required = false) ReportState state,
            @PageableDefault(
                    size = 20,
                    sort = "submittedAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        return reportService.findAccessible(
                programId,
                state,
                pageable
        );
    }

    @GetMapping("/reports")
    public Page<ReportResponse> findAccessible(
            @RequestParam(required = false) UUID programId,
            @RequestParam(required = false) ReportState state,
            @PageableDefault(
                    size = 20,
                    sort = "submittedAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        return reportService.findAccessible(
                programId,
                state,
                pageable
        );
    }

    @GetMapping("/reports/mine")
    public Page<ReportResponse> findMine(
            @PageableDefault(
                    size = 20,
                    sort = "submittedAt",
                    direction = Sort.Direction.DESC
            )
            Pageable pageable
    ) {
        return reportService.findMine(pageable);
    }

    @GetMapping("/reports/{id}")
    public ReportResponse findById(@PathVariable UUID id) {
        return reportService.findById(id);
    }

    @PatchMapping("/reports/{id}/triage")
    public ReportResponse triage(
            @PathVariable UUID id,
            @Valid @RequestBody TriageReportRequest request
    ) {
        return reportService.triage(id, request);
    }

    @PatchMapping("/reports/{id}/disclosure-status")
    public ReportResponse updateDisclosureStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDisclosureStateRequest request
    ) {
        return reportService.updateDisclosureStatus(id, request);
    }

    @PostMapping("/reports/{id}/rewards")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse recordReward(
            @PathVariable UUID id,
            @Valid @RequestBody RewardReportRequest request
    ) {
        return reportService.recordReward(id, request);
    }
}
