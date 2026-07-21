package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService reportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateReportResponse createNew(
            @Valid @RequestBody CreateReportRequest request
    ) {
        return reportService.createNew(request);
    }
}