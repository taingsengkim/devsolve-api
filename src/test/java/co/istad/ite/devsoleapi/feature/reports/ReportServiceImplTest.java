package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.ReportMapper;
import co.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import co.istad.ite.devsoleapi.feature.reports.entities.Report;
import co.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import co.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import co.istad.ite.devsoleapi.feature.reports.enums.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportServiceImplTest {

    private ReportRepository reportRepository;
    private ReportMapper reportMapper;
    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        reportRepository = mock(ReportRepository.class);
        reportMapper = Mappers.getMapper(ReportMapper.class);
        reportService = new ReportServiceImpl(reportRepository, reportMapper);
    }

    @Test
    void createNew_shouldPopulateAllRequiredFieldsAndSave() {
        UUID programId = UUID.randomUUID();
        CreateReportRequest request = new CreateReportRequest();
        request.setTitle("SQL Injection Vulnerability");
        request.setVulnerabilityInformation("Found SQLi in endpoint /api/data");
        request.setImpact("Data leakage");
        request.setSeverity(Severity.HIGH);

        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> {
            Report r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        ReportResponse response = reportService.createNew(programId, request);

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(reportCaptor.capture());

        Report savedReport = reportCaptor.getValue();

        assertNotNull(savedReport);
        assertEquals(programId, savedReport.getProgramId());
        assertEquals("SQL Injection Vulnerability", savedReport.getTitle());
        assertEquals("Found SQLi in endpoint /api/data", savedReport.getVulnerabilityInformation());
        assertEquals(Severity.HIGH, savedReport.getReportedSeverity());
        assertNotNull(savedReport.getReporterId());
        assertEquals(ReportState.NEW, savedReport.getState());
        assertEquals(DisclosureStatus.NOT_DISCLOSED, savedReport.getDisclosureStatus());

        assertNotNull(response);
        assertEquals("SQL Injection Vulnerability", response.getTitle());
        assertEquals(Severity.HIGH, response.getSeverity());
    }
}
