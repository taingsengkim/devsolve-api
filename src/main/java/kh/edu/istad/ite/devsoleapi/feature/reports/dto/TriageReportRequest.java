package kh.edu.istad.ite.devsoleapi.feature.reports.dto;

import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TriageReportRequest {

    private ReportState state;
}

