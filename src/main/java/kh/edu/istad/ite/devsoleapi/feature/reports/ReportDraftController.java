package kh.edu.istad.ite.devsoleapi.feature.reports;

import jakarta.validation.Valid;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportDraftResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.SaveReportDraftRequest;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * A report in progress. Drafts are private to the reporter who owns them —
 * they are never visible to the program, never enter triage, and never count
 * as a submission. Only {@code POST /report-drafts/{id}/submit} files one.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ReportDraftController {

    private final ReportDraftService reportDraftService;

    @PostMapping("/programs/{programId}/report-drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportDraftResponse create(
            @PathVariable UUID programId,
            @Valid @RequestBody SaveReportDraftRequest request
    ) {
        return reportDraftService.create(programId, request);
    }

    /**
     * @param programId narrows to one program's drafts. Omitted, returns every
     *                  draft the caller owns, newest edit first — which is what
     *                  a "continue where you left off" list wants.
     */
    @GetMapping("/report-drafts")
    public Page<ReportDraftResponse> findMine(
            @RequestParam(required = false) UUID programId,
            @PageableDefault(
                    size = 20,
                    sort = "updatedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return reportDraftService.findMine(programId, pageable);
    }

    @GetMapping("/report-drafts/{id}")
    public ReportDraftResponse findById(@PathVariable UUID id) {
        return reportDraftService.findById(id);
    }

    /**
     * The autosave target. A whole-draft replace rather than a patch, so
     * repeating it is harmless and a field the client stops sending is
     * cleared rather than silently kept.
     */
    @PutMapping("/report-drafts/{id}")
    public ReportDraftResponse save(
            @PathVariable UUID id,
            @Valid @RequestBody SaveReportDraftRequest request
    ) {
        return reportDraftService.save(id, request);
    }

    @DeleteMapping("/report-drafts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        reportDraftService.delete(id);
    }

    /**
     * Files the draft as a report and discards it. Returns the report, so the
     * client can navigate straight to it. A rejected submission leaves the
     * draft exactly as it was.
     */
    @PostMapping("/report-drafts/{id}/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse submit(@PathVariable UUID id) {
        return reportDraftService.submit(id);
    }
}
