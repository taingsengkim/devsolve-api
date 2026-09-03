package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import kh.edu.istad.ite.devsoleapi.feature.ai.AiUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The gate that decides what reaches the public feed unread.
 *
 * <p>Every case here is really the same assertion: anything short of an
 * unambiguous yes leaves the post in the queue it was already in.
 *
 * <p>Each hold also asserts its {@link AutoApprovalHold}, because that is what
 * decides whether the author hears about it. Getting the category wrong is not
 * a visible bug — the post is held either way — it just means an author is
 * either told nothing or told something about an outage they cannot act on.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentAutoApproverTest {

    private static final String TITLE = "NullPointerException on startup";
    private static final String PROSE =
            "The application fails to boot after upgrading Spring Boot.";

    @Mock
    private AutoApprovalService settings;
    @Mock
    private ContentApprovalReviewer reviewer;

    @Test
    void aSafeOnTopicSubmissionIsApproved() {
        enabled();
        when(reviewer.review(any(), any(), any())).thenReturn(
                new ContentApprovalVerdict(true, true, 95, "A Spring Boot bug")
        );

        AutoApprovalDecision decision = decide();

        assertTrue(decision.approved());
        assertNull(decision.hold());
    }

    /**
     * The switch is checked before anything else, so turning it off stops the
     * spend as well as the publishing.
     */
    @Test
    void nothingIsReviewedWhileTheSwitchIsOff() {
        when(settings.isEnabled(AutoApprovalTarget.PROBLEM)).thenReturn(false);

        AutoApprovalDecision decision = decide();

        assertFalse(decision.approved());
        assertEquals(AutoApprovalHold.NOT_CHECKED, decision.hold());
        verifyNoInteractions(reviewer);
    }

    @Test
    void nothingIsApprovedWhenNoModelIsConfigured() {
        when(settings.isEnabled(AutoApprovalTarget.PROBLEM)).thenReturn(true);
        when(reviewer.isEnabled()).thenReturn(false);

        AutoApprovalDecision decision = decide();

        assertFalse(decision.approved());
        assertEquals(AutoApprovalHold.NOT_CHECKED, decision.hold());
        verify(reviewer, never()).review(any(), any(), any());
    }

    /**
     * The word list is local and deterministic. Anything it catches is held
     * without paying for a model call to agree.
     */
    @Test
    void theWordListHoldsAPostBeforeTheModelIsAsked() {
        enabled();

        AutoApprovalDecision decision = approver().decide(
                AutoApprovalTarget.PROBLEM,
                "why is this shit broken",
                PROSE
        );

        assertFalse(decision.approved());
        assertEquals(AutoApprovalHold.UNSAFE, decision.hold());
        verify(reviewer, never()).review(any(), any(), any());
    }

    @Test
    void anUnsafeSubmissionIsHeld() {
        enabled();
        when(reviewer.review(any(), any(), any())).thenReturn(
                new ContentApprovalVerdict(true, false, 99, "Targets a person")
        );

        AutoApprovalDecision decision = decide();

        assertFalse(decision.approved());
        assertEquals(AutoApprovalHold.UNSAFE, decision.hold());
    }

    /**
     * The whole point of the topic half: a perfectly polite recipe is still
     * not what this platform is for.
     */
    @Test
    void anOffTopicSubmissionIsHeld() {
        enabled();
        when(reviewer.review(any(), any(), any())).thenReturn(
                new ContentApprovalVerdict(false, true, 99, "A recipe")
        );

        AutoApprovalDecision decision = decide();

        assertFalse(decision.approved());
        assertEquals(AutoApprovalHold.OFF_TOPIC, decision.hold());
    }

    @Test
    void anUnsureModelHoldsThePostForAPerson() {
        enabled();
        when(reviewer.review(any(), any(), any())).thenReturn(
                new ContentApprovalVerdict(
                        true,
                        true,
                        ContentAutoApprover.MINIMUM_CONFIDENCE - 1,
                        "Probably fine"
                )
        );

        AutoApprovalDecision decision = decide();

        assertFalse(decision.approved());
        assertEquals(AutoApprovalHold.UNCLEAR, decision.hold());
    }

    @Test
    void theThresholdItselfIsEnough() {
        enabled();
        when(reviewer.review(any(), any(), any())).thenReturn(
                new ContentApprovalVerdict(
                        true,
                        true,
                        ContentAutoApprover.MINIMUM_CONFIDENCE,
                        "Fine"
                )
        );

        assertTrue(decide().approved());
    }

    /**
     * Out of quota, unreachable, refused. The post waits, which is what it
     * would have done if this feature had never been switched on — and the
     * author is told nothing, for the same reason.
     */
    @Test
    void aModelThatCannotAnswerHoldsThePost() {
        enabled();
        when(reviewer.review(any(), any(), any()))
                .thenThrow(new AiUnavailableException("out of quota"));

        AutoApprovalDecision decision = decide();

        assertFalse(decision.approved());
        assertEquals(AutoApprovalHold.NOT_CHECKED, decision.hold());
        assertFalse(decision.hold().isAboutTheSubmission());
    }

    @Test
    void anEmptyAnswerHoldsThePost() {
        enabled();
        when(reviewer.review(any(), any(), any())).thenReturn(null);

        AutoApprovalDecision decision = decide();

        assertFalse(decision.approved());
        assertEquals(AutoApprovalHold.NOT_CHECKED, decision.hold());
    }

    /**
     * Each kind has its own switch, so turning showcases on must not publish
     * problems.
     */
    @Test
    void eachKindIsSwitchedIndependently() {
        when(settings.isEnabled(AutoApprovalTarget.SHOWCASE)).thenReturn(true);
        when(settings.isEnabled(AutoApprovalTarget.PROBLEM)).thenReturn(false);
        when(reviewer.isEnabled()).thenReturn(true);
        when(reviewer.review(eq(AutoApprovalTarget.SHOWCASE), any(), any()))
                .thenReturn(new ContentApprovalVerdict(true, true, 95, "ok"));

        assertFalse(decide().approved());
        assertTrue(approver().decide(
                AutoApprovalTarget.SHOWCASE,
                TITLE,
                PROSE
        ).approved());
    }

    private AutoApprovalDecision decide() {
        return approver().decide(AutoApprovalTarget.PROBLEM, TITLE, PROSE);
    }

    private ContentAutoApprover approver() {
        return new ContentAutoApprover(settings, reviewer);
    }

    private void enabled() {
        when(settings.isEnabled(any())).thenReturn(true);
        when(reviewer.isEnabled()).thenReturn(true);
    }
}
