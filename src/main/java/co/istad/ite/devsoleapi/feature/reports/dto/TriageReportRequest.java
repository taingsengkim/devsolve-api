package co.istad.ite.devsoleapi.feature.reports.dto;

import co.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TriageReportRequest {

    private ReportState state;
}

