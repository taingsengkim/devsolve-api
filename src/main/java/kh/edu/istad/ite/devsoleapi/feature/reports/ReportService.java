package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RewardReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.UpdateDisclosureStateRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.UUID;

public interface ReportService {

    ReportResponse create(UUID programId, CreateReportRequest request);

    ReportResponse findById(UUID id);

    Page<ReportResponse> findAccessible(
            UUID programId,
            ReportState state,
            Pageable pageable
    );

    Page<ReportResponse> findMine(Pageable pageable);

    ReportResponse triage(UUID id, TriageReportRequest request);

    ReportResponse updateDisclosureStatus(
            UUID id,
            UpdateDisclosureStateRequest request
    );

    ReportResponse recordReward(UUID id, RewardReportRequest request);

    ReportResponse uploadAttachment(UUID reportId, MultipartFile file);

    void removeAttachment(UUID reportId, UUID attachmentId);

    URI createAttachmentDownloadUrl(
            UUID reportId,
            UUID attachmentId
    );

    void requireViewAccess(UUID reportId);

    ReportDiscussionAccess requireDiscussionAccess(UUID reportId);
}
