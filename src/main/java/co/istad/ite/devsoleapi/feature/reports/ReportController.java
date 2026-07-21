package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportResponse;
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
    public CreateReportResponse createNew(
            @PathVariable UUID programId,
            @Valid @RequestBody CreateReportRequest request
    ) {
        return reportService.createNew(programId, request);
    }
}