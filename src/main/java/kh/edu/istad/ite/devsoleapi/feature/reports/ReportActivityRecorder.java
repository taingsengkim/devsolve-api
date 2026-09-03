package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportActivity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportActivityType;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * The only thing that writes a report's history.
 *
 * <p>One writer rather than a {@code save} beside each transition, because the
 * history is only worth anything if it is complete: a transition that forgets
 * to record itself leaves a gap nobody notices until the gap is the thing being
 * argued about. Everything that moves a report goes through here.
 *
 * <p>Runs inside the caller's transaction on purpose. An activity row that
 * survived a rolled-back triage would describe something that did not happen,
 * which is worse than no history at all.
 *
 * <p>This is also where a report learns it has been answered — see
 * {@link #stampFirstResponse}.
 */
@Component
@RequiredArgsConstructor
public class ReportActivityRecorder {

    private final ReportActivityRepository reportActivityRepository;

    /** The reporter filing it. The clock on the program's response starts here. */
    public void submitted(Report report, UserProfile reporter) {
        record(ReportActivity.builder()
                .report(report)
                .actor(reporter)
                .activityType(ReportActivityType.SUBMITTED)
                .toState(report.getState())
                .severity(report.getReportedSeverity())
                .build());
    }

    /**
     * A triage decision. Recorded even when the state does not move — a
     * re-triage that only changes severity is still somebody acting on the
     * report, and leaving it out would make the timeline disagree with
     * {@code triagedAt}.
     */
    public void stateChanged(
            Report report,
            UserProfile actor,
            ReportState fromState,
            ReportState toState,
            Severity severity,
            String detail
    ) {
        record(ReportActivity.builder()
                .report(report)
                .actor(actor)
                .activityType(
                        fromState == toState
                                ? ReportActivityType.SEVERITY_CHANGED
                                : ReportActivityType.STATE_CHANGED
                )
                .fromState(fromState)
                .toState(toState)
                .severity(severity)
                .detail(detail)
                .build());
    }

    public void rewardGranted(
            Report report,
            UserProfile actor,
            String detail
    ) {
        record(ReportActivity.builder()
                .report(report)
                .actor(actor)
                .activityType(ReportActivityType.REWARD_GRANTED)
                .detail(detail)
                .build());
    }

    public void retestRequested(
            Report report,
            UserProfile actor,
            ReportState fromState,
            int attemptNumber
    ) {
        record(ReportActivity.builder()
                .report(report)
                .actor(actor)
                .activityType(ReportActivityType.RETEST_REQUESTED)
                .fromState(fromState)
                .toState(ReportState.RETESTING)
                .detail("Retest attempt " + attemptNumber + " requested")
                .build());
    }

    public void retestSubmitted(
            Report report,
            UserProfile actor,
            ReportState toState,
            String detail
    ) {
        record(ReportActivity.builder()
                .report(report)
                .actor(actor)
                .activityType(ReportActivityType.RETEST_SUBMITTED)
                .fromState(ReportState.RETESTING)
                .toState(toState)
                .detail(detail)
                .build());
    }

    /**
     * Nobody answered in time. No actor: the platform closed the attempt, and
     * attributing that to the last person who touched the report would be a lie
     * the timeline is specifically there to prevent.
     */
    public void retestExpired(Report report, int attemptNumber) {
        record(ReportActivity.builder()
                .report(report)
                .activityType(ReportActivityType.RETEST_EXPIRED)
                .fromState(ReportState.RETESTING)
                .toState(report.getState())
                .detail("Retest attempt " + attemptNumber
                        + " lapsed with no answer")
                .build());
    }

    public void disclosureChanged(
            Report report,
            UserProfile actor,
            String detail
    ) {
        record(ReportActivity.builder()
                .report(report)
                .actor(actor)
                .activityType(ReportActivityType.DISCLOSURE_CHANGED)
                .detail(detail)
                .build());
    }

    private void record(ReportActivity activity) {
        reportActivityRepository.save(activity);
        stampFirstResponse(activity);
    }

    /**
     * Stamps the first time anybody but the reporter acted on the report.
     *
     * <p>Here rather than in the triage path, because the first response is not
     * always a triage — a program that asks a question, records a reward or
     * requests a retest has responded, and the number researchers judge a
     * program by should not depend on which of those it did first.
     *
     * <p>A null actor does not count. A retest lapsing on its own deadline is
     * the platform noticing silence, and counting it would let a program earn a
     * response time by never answering.
     */
    private void stampFirstResponse(ReportActivity activity) {
        Report report = activity.getReport();
        if (report.getFirstRespondedAt() != null
                || activity.getActor() == null
                || report.getReporter() == null
                || Objects.equals(
                        activity.getActor().getId(),
                        report.getReporter().getId()
                )) {
            return;
        }
        report.setFirstRespondedAt(LocalDateTime.now());
    }
}
