package co.istad.ite.devsoleapi.feature.reports.dto;

import co.istad.ite.devsoleapi.feature.reports.entities.Report;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReportMapper {

    @Mapping(target = "reportedSeverity", source = "severity")
    Report toEntity(CreateReportRequest request);

    @Mapping(target = "severity", expression = "java(report.getSeverity() != null ? report.getSeverity() : report.getReportedSeverity())")
    ReportResponse toResponse(Report report);

    List<ReportResponse> toResponse(List<Report> reports);
}