package co.istad.ite.devsoleapi.feature.reports.dto;

import co.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import co.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import co.istad.ite.devsoleapi.feature.reports.enums.Severity;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@Builder
public class ReportResponse {

    private UUID id;

    private String title;

    private String vulnerabilityInformation;

    private String impact;

    private Severity severity;

    private BigDecimal cvssScore;

    private ReportState state;

    private DisclosureStatus disclosureStatus;

    private Map<String, Object> weakness;

    private Map<String, Object> asset;

    private Map<String, Object> attachments;

    private Instant submittedAt;

    private Instant createdAt;
}
