package co.istad.ite.devsoleapi.feature.reports;

import co.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import co.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import co.istad.ite.devsoleapi.feature.reports.dto.UpdateDisclosureStateRequest;

import java.util.UUID;

public interface ReportService {

    /**
     * Creates a new report for a specific program.
     *
     * @param programId the unique identifier of the program for which the report is being created
     * @param request   the payload containing the details of the report to be created
     * @return a {@link ReportResponse} containing the newly created report's details
     */
    ReportResponse createNew(UUID programId, CreateReportRequest request);


    /**
     * Retrieves the details of a specific report by its unique identifier.
     *
     * @param id the unique identifier of the report to retrieve
     * @return a {@link ReportResponse} containing the report's details
     */
    ReportResponse findById(UUID id);


    /**
     * Triages an existing report, updating its status, severity, or other relevant triage details.
     *
     * @param id      the unique identifier of the report to triage
     * @param request the payload containing the triage update details
     * @return a {@link ReportResponse} reflecting the updated state of the report
     */
    ReportResponse triage(UUID id, TriageReportRequest request);


    /**
     * Updates the disclosure state of an existing report.
     *
     * @param id      the unique identifier of the report to update disclosure state
     * @param request the payload containing the updated disclosure state
     * @return a {@link ReportResponse} reflecting the updated state of the report
     */
    ReportResponse updateDisclosureState(UUID id, UpdateDisclosureStateRequest request);
}
