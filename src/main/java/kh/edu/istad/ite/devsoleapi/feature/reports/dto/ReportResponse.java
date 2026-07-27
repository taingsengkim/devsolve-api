package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportAttachment;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.AssetType;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.DisclosureStatus;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.Severity;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

    private UUID weaknessId;

    private AssetType assetType;

    private ReportState state;

    private DisclosureStatus disclosureStatus;

    private Map<String, Object> weakness;

    private List<ReportAttachment> attachments;

    private Instant submittedAt;

    private Instant createdAt;
}
