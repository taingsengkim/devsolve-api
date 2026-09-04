package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateReportRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.RejectTriageSeverityRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.ReportActivityResponse;
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

    /**
     * How the report got to its current state, oldest entry first.
     *
     * <p>Readable by anyone who can read the report. Nothing on the timeline is
     * written by a person — it is the platform's own record of what happened —
     * so there is nothing here the reporter may not see.
     */
    List<ReportActivityResponse> findActivities(UUID reportId);

    ReportResponse triage(UUID id, TriageReportRequest request);

    /**
     * The reporter agreeing to a severity triage rated differently from their
     * own claim. Settles the disagreement without an administrator, which is
     * what most of them deserve, and unblocks the report.
     */
    ReportResponse acceptTriageSeverity(UUID reportId);

    /**
     * The reporter refusing it. This is what opens the dispute proper: the
     * report stays without an agreed severity, and an administrator rules.
     */
    ReportResponse rejectTriageSeverity(
            UUID reportId,
            RejectTriageSeverityRequest request
    );

    /**
     * Severity disagreements whose window has run out, oldest deadline first.
     *
     * <p>Read separately from {@link #autoAcceptTriageSeverity} so the sweep
     * can settle each on its own — one row that cannot be closed should cost
     * that report its deadline, not every other report's.
     */
    List<UUID> findOverdueSeverityDisputeIds();

    /**
     * Settles one unanswered disagreement at the triage severity. Does nothing
     * if it has been answered or ruled on since it was listed.
     */
    void autoAcceptTriageSeverity(UUID disputeId);

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
