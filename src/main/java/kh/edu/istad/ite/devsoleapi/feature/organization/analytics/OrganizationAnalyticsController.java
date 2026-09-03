package kh.edu.istad.ite.devsoleapi.feature.organization.analytics;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import kh.edu.istad.ite.devsoleapi.common.exception.RestErrorResponse;
import kh.edu.istad.ite.devsoleapi.feature.organization.analytics.dto.OrganizationAnalyticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import static kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService.ORGANIZATION_PARAMETER_DESCRIPTION;

/**
 * The company analytics dashboard.
 *
 * <p>Two ways in to the same figures: {@code /me/analytics} for the workspace
 * a person is signed in to, {@code /{id}/analytics} for a client that already
 * has the id. Both need {@code VIEW_PROGRAMS} at that organization.
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationAnalyticsController {

    private static final String NOT_A_MEMBER =
            "The caller is not at an active organization, or lacks "
                    + "VIEW_PROGRAMS there. Distinct from an empty dashboard: "
                    + "nothing was withheld silently.";
    private static final String AMBIGUOUS_ORGANIZATION =
            "The caller is at more than one organization and named none. "
                    + "errorDetails.organizationIds lists them.";
    private static final String UNKNOWN_PROGRAM =
            "programId is not a live program of this organization.";
    private static final String TIME_RANGE_DESCRIPTION =
            "The window to report on. One of: 30d, 90d, 6m, 1y, all. "
                    + "Defaults to 6m.";
    private static final String PROGRAM_DESCRIPTION =
            "Narrow every figure to one program. Omit for every program the "
                    + "organization runs.";

    private static final String CSV_FORMAT = "csv";

    /**
     * Prefixed to the export. Without it Excel on Windows reads the file in
     * the system code page, and a researcher's name comes out mojibake in the
     * one artefact meant to leave the tool.
     */
    private static final String UTF8_BOM = "\uFEFF";

    private final OrganizationAnalyticsService analyticsService;

    @ApiResponse(
            responseCode = "403",
            description = NOT_A_MEMBER,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = UNKNOWN_PROGRAM,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "409",
            description = AMBIGUOUS_ORGANIZATION,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @GetMapping("/me/analytics")
    public OrganizationAnalyticsResponse myAnalytics(
            @Parameter(description = ORGANIZATION_PARAMETER_DESCRIPTION)
            @RequestParam(required = false) UUID organizationId,
            @Parameter(description = TIME_RANGE_DESCRIPTION)
            @RequestParam(required = false) String timeRange,
            @Parameter(description = PROGRAM_DESCRIPTION)
            @RequestParam(required = false) UUID programId
    ) {
        return analyticsService.getAnalytics(
                organizationId,
                timeRange,
                programId
        );
    }

    @ApiResponse(
            responseCode = "403",
            description = NOT_A_MEMBER,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "404",
            description = UNKNOWN_PROGRAM,
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @GetMapping("/{organizationId}/analytics")
    public OrganizationAnalyticsResponse analytics(
            @PathVariable UUID organizationId,
            @Parameter(description = TIME_RANGE_DESCRIPTION)
            @RequestParam(required = false) String timeRange,
            @Parameter(description = PROGRAM_DESCRIPTION)
            @RequestParam(required = false) UUID programId
    ) {
        return analyticsService.getAnalytics(
                organizationId,
                timeRange,
                programId
        );
    }

    /**
     * The same dashboard as a spreadsheet, off the same cached figures as the
     * JSON so the export cannot disagree with what is on screen.
     */
    @ApiResponse(
            responseCode = "400",
            description = "format is not csv. PDF is a rendering concern and "
                    + "is not produced here.",
            content = @Content(schema = @Schema(
                    implementation = RestErrorResponse.class))
    )
    @GetMapping(
            value = "/me/analytics/export",
            produces = "text/csv"
    )
    public ResponseEntity<byte[]> exportMyAnalytics(
            @Parameter(description = ORGANIZATION_PARAMETER_DESCRIPTION)
            @RequestParam(required = false) UUID organizationId,
            @Parameter(description = TIME_RANGE_DESCRIPTION)
            @RequestParam(required = false) String timeRange,
            @Parameter(description = PROGRAM_DESCRIPTION)
            @RequestParam(required = false) UUID programId,
            @Parameter(description = "csv, the only format produced here")
            @RequestParam(required = false, defaultValue = CSV_FORMAT)
            String format
    ) {
        return export(organizationId, timeRange, programId, format);
    }

    @GetMapping(
            value = "/{organizationId}/analytics/export",
            produces = "text/csv"
    )
    public ResponseEntity<byte[]> exportAnalytics(
            @PathVariable UUID organizationId,
            @Parameter(description = TIME_RANGE_DESCRIPTION)
            @RequestParam(required = false) String timeRange,
            @Parameter(description = PROGRAM_DESCRIPTION)
            @RequestParam(required = false) UUID programId,
            @Parameter(description = "csv, the only format produced here")
            @RequestParam(required = false, defaultValue = CSV_FORMAT)
            String format
    ) {
        return export(organizationId, timeRange, programId, format);
    }

    private ResponseEntity<byte[]> export(
            UUID organizationId,
            String timeRange,
            UUID programId,
            String format
    ) {
        requireCsv(format);

        OrganizationAnalyticsResponse analytics = analyticsService.getAnalytics(
                organizationId,
                timeRange,
                programId
        );
        byte[] body = (UTF8_BOM + AnalyticsCsvRenderer.render(analytics))
                .getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(new MediaType(
                        "text",
                        "csv",
                        StandardCharsets.UTF_8
                ))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        AnalyticsCsvRenderer.fileName(analytics),
                                        StandardCharsets.UTF_8
                                )
                                .build()
                                .toString()
                )
                .body(body);
    }

    private void requireCsv(String format) {
        if (format == null || format.isBlank()) {
            return;
        }
        if (!CSV_FORMAT.equals(format.trim().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported export format '" + format
                            + "'. Only csv is produced here."
            );
        }
    }
}
