package kh.edu.istad.ite.devsoleapi.feature.reports;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportActivityResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RequestRetestRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RewardReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.SubmitRetestRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.UpdateDisclosureStateRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
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
            @ParameterObject
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
            @ParameterObject
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
            @ParameterObject
            Pageable pageable
    ) {
        return reportService.findMine(pageable);
    }

    @GetMapping("/reports/{id}")
    public ReportResponse findById(@PathVariable UUID id) {
        return reportService.findById(id);
    }

    /**
     * How this report got to where it is, oldest entry first.
     *
     * <p>Unpaged: a report's history is bounded by how many times people acted
     * on it, which is tens at the very most, and a timeline read a page at a
     * time is a timeline nobody renders correctly.
     */
    @GetMapping("/reports/{id}/activities")
    public List<ReportActivityResponse> findActivities(@PathVariable UUID id) {
        return reportService.findActivities(id);
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

    /**
     * The program's side of a retest: a fix is deployed, please check it.
     * Needs TRIAGE_REPORTS or MANAGE_PROGRAM_STATE on the owning organization.
     */
    @PostMapping("/reports/{id}/retest/request")
    public ReportResponse requestRetest(
            @PathVariable UUID id,
            @Valid @RequestBody RequestRetestRequest request
    ) {
        return reportService.requestRetest(id, request);
    }

    /** The researcher's verdict. Only the reporter may submit one. */
    @PostMapping("/reports/{id}/retest/submit")
    public ReportResponse submitRetest(
            @PathVariable UUID id,
            @Valid @RequestBody SubmitRetestRequest request
    ) {
        return reportService.submitRetest(id, request);
    }

    @PostMapping(
            value = "/reports/{id}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse uploadAttachment(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file
    ) {
        return reportService.uploadAttachment(id, file);
    }

    @DeleteMapping("/reports/{id}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId
    ) {
        reportService.removeAttachment(id, attachmentId);
    }

    @GetMapping("/reports/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Void> downloadAttachment(
            @PathVariable UUID id,
            @PathVariable UUID attachmentId
    ) {
        URI downloadUrl = reportService.createAttachmentDownloadUrl(
                id,
                attachmentId
        );
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(downloadUrl)
                .build();
    }
}
