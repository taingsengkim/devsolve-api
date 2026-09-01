package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RequestRetestRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RewardReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.SubmitRetestRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.TriageReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.UpdateDisclosureStateRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
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

    /**
     * Puts a confirmed report into retest: a fix is deployed and the researcher
     * who found the bug is asked to confirm it holds.
     */
    ReportResponse requestRetest(UUID id, RequestRetestRequest request);

    /** The researcher's answer to an open retest. */
    ReportResponse submitRetest(UUID id, SubmitRetestRequest request);

    /**
     * Open retests nobody answered in time, oldest deadline first.
     *
     * <p>Read separately from {@link #expireRetest} so that the sweep can lapse
     * each one on its own — a single attempt that cannot be closed should cost
     * that report its expiry, not every other report's.
     */
    List<UUID> findOverdueRetestIds();

    /**
     * Lapses one overdue retest: the attempt closes with no verdict and the
     * report goes back to RESOLVED, where it was before the retest was asked
     * for. Does nothing if the attempt has been answered or closed since it was
     * listed.
     */
    void expireRetest(UUID retestId);

    ReportResponse uploadAttachment(UUID reportId, MultipartFile file);

    void removeAttachment(UUID reportId, UUID attachmentId);

    URI createAttachmentDownloadUrl(
            UUID reportId,
            UUID attachmentId
    );

    void requireViewAccess(UUID reportId);

    ReportDiscussionAccess requireDiscussionAccess(UUID reportId);
}
