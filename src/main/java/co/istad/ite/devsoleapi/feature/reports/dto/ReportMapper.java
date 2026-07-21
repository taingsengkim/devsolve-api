package co.istad.ite.devsoleapi.feature.reports.dto;

import co.istad.ite.devsoleapi.feature.reports.Report;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ReportMapper {

    Report toEntity(CreateReportRequest request);

    ReportResponse toResponse(Report report);
}