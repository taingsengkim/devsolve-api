package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Report;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.ReportActivity;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportActivityType;
import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

/**
 * The report's own record of what happened to it, and the response clock it
 * carries.
 */
@ExtendWith(MockitoExtension.class)
class ReportActivityRecorderTest {

    @Mock
    private ReportActivityRepository reportActivityRepository;

    @InjectMocks
    private ReportActivityRecorder recorder;

    private UserProfile reporter;
    private UserProfile triager;
    private Report report;

    @BeforeEach
    void setUp() {
        reporter = user();
        triager = user();
        report = Report.builder()
                .id(UUID.randomUUID())
                .reporter(reporter)
                .state(ReportState.NEW)
                .reportedSeverity(Severity.HIGH)
                .build();
    }

    /**
     * Filing a report is not a response to it. If it were, every program would
     * show a first response time of zero.
     */
    @Test
    void filingTheReportDoesNotStartTheResponseClock() {
        recorder.submitted(report, reporter);

        assertNull(report.getFirstRespondedAt());
        ReportActivity saved = captured();
        assertEquals(ReportActivityType.SUBMITTED, saved.getActivityType());
        assertEquals(ReportState.NEW, saved.getToState());
    }

    @Test
    void theFirstActionByAnybodyElseStampsTheResponse() {
        recorder.stateChanged(
                report,
                triager,
                ReportState.NEW,
                ReportState.TRIAGING,
                Severity.HIGH,
                null
        );

        assertNotNull(report.getFirstRespondedAt());
    }

    /**
     * "First" means first. A program that answers in an hour and triages again
     * a month later has still answered in an hour, which is the whole reason
     * this is not read off triagedAt.
     */
    @Test
    void aLaterActionDoesNotMoveTheStamp() {
        LocalDateTime alreadyAnswered = LocalDateTime.now().minusDays(30);
        report.setFirstRespondedAt(alreadyAnswered);

        recorder.stateChanged(
                report,
                triager,
                ReportState.TRIAGING,
                ReportState.RESOLVED,
                Severity.HIGH,
                null
        );

        assertEquals(alreadyAnswered, report.getFirstRespondedAt());
    }

    /**
     * A retest lapsing is the platform noticing silence. Counting it would let
     * a program earn a response time by never answering.
     */
    @Test
    void theClockLapsingIsNotAResponse() {
        recorder.retestExpired(report, 2);

        assertNull(report.getFirstRespondedAt());
        ReportActivity saved = captured();
        assertNull(saved.getActor());
        assertEquals(
                ReportActivityType.RETEST_EXPIRED,
                saved.getActivityType()
        );
    }

    /**
     * A reporter can act on their own report — submitting a retest verdict is
     * the obvious one — and none of it is the program responding.
     */
    @Test
    void theReporterActingOnTheirOwnReportIsNotAResponse() {
        recorder.retestSubmitted(
                report,
                reporter,
                ReportState.VALID_CONFIRMED,
                "Still reproducible"
        );

        assertNull(report.getFirstRespondedAt());
    }

    /**
     * A re-triage that only moves the severity is still somebody acting on the
     * report, and has to be distinguishable from one that moved its state.
     */
    @Test
    void aTriageThatDoesNotMoveTheStateIsASeverityChange() {
        recorder.stateChanged(
                report,
                triager,
                ReportState.TRIAGING,
                ReportState.TRIAGING,
                Severity.CRITICAL,
                null
        );

        ReportActivity saved = captured();
        assertEquals(
                ReportActivityType.SEVERITY_CHANGED,
                saved.getActivityType()
        );
        assertEquals(Severity.CRITICAL, saved.getSeverity());
    }

    @Test
    void aMoveBetweenStatesIsAStateChange() {
        recorder.stateChanged(
                report,
                triager,
                ReportState.NEW,
                ReportState.REJECTED,
                null,
                "Out of scope"
        );

        ReportActivity saved = captured();
        assertEquals(
                ReportActivityType.STATE_CHANGED,
                saved.getActivityType()
        );
        assertEquals(ReportState.NEW, saved.getFromState());
        assertEquals(ReportState.REJECTED, saved.getToState());
        assertEquals("Out of scope", saved.getDetail());
    }

    /**
     * A reward is not a transition, and a client rendering the timeline has to
     * be able to tell the two apart without knowing every type by name.
     */
    @Test
    void aRewardCarriesNoStates() {
        recorder.rewardGranted(report, triager, "Awarded 500");

        ReportActivity saved = captured();
        assertNull(saved.getFromState());
        assertNull(saved.getToState());
        assertEquals("Awarded 500", saved.getDetail());
    }

    private ReportActivity captured() {
        ArgumentCaptor<ReportActivity> captor =
                ArgumentCaptor.forClass(ReportActivity.class);
        verify(reportActivityRepository).save(captor.capture());
        return captor.getValue();
    }

    private UserProfile user() {
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        return profile;
    }
}
