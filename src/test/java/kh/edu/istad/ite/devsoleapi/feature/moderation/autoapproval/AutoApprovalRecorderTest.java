package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The row a pending post points at to explain itself.
 */
@ExtendWith(MockitoExtension.class)
class AutoApprovalRecorderTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID CONTENT = UUID.randomUUID();

    @Mock
    private ContentAutoReviewRepository reviewRepository;

    @InjectMocks
    private AutoApprovalRecorder recorder;

    @Test
    void aHoldIsStoredWithItsCategoryAndReason() {
        when(reviewRepository.findByTargetAndContentId(
                AutoApprovalTarget.PROBLEM,
                CONTENT
        )).thenReturn(Optional.empty());

        recorder.record(
                event(AutoApprovalTarget.PROBLEM, "Outbox poller races"),
                AutoApprovalDecision.hold(
                        AutoApprovalHold.UNCLEAR,
                        "Too little to go on"
                )
        );

        ContentAutoReview saved = captureSaved();
        assertEquals(AutoApprovalTarget.PROBLEM, saved.getTarget());
        assertEquals(CONTENT, saved.getContentId());
        assertEquals(AUTHOR, saved.getAuthorId());
        assertEquals("Outbox poller races", saved.getTitle());
        assertFalse(saved.isApproved());
        assertEquals(AutoApprovalHold.UNCLEAR, saved.getHold());
        assertEquals("Too little to go on", saved.getReason());
        assertTrue(saved.getCheckedAt() != null);
    }

    /**
     * Every outcome, including the ones nobody is notified about — "the check is
     * switched off" is a perfectly good answer to why a post is queued, it is
     * just not worth interrupting somebody with.
     */
    @Test
    void anUncheckedSubmissionIsRecordedToo() {
        when(reviewRepository.findByTargetAndContentId(
                AutoApprovalTarget.SHOWCASE,
                CONTENT
        )).thenReturn(Optional.empty());

        recorder.record(
                event(AutoApprovalTarget.SHOWCASE, "A build log parser"),
                AutoApprovalDecision.hold(
                        AutoApprovalHold.NOT_CHECKED,
                        "Auto-approval is off"
                )
        );

        ContentAutoReview saved = captureSaved();
        assertFalse(saved.isApproved());
        assertEquals(AutoApprovalHold.NOT_CHECKED, saved.getHold());
    }

    @Test
    void anApprovalClearsTheHold() {
        when(reviewRepository.findByTargetAndContentId(
                AutoApprovalTarget.PROBLEM,
                CONTENT
        )).thenReturn(Optional.empty());

        recorder.record(
                event(AutoApprovalTarget.PROBLEM, "A well written problem"),
                AutoApprovalDecision.approve("Safe and on topic")
        );

        ContentAutoReview saved = captureSaved();
        assertTrue(saved.isApproved());
        assertNull(saved.getHold());
    }

    /**
     * An author who edits a queued post has it checked again, and the row
     * answers "why is this post where it is now" — so the new verdict replaces
     * the old one rather than joining it.
     */
    @Test
    void aSecondCheckReplacesTheFirstVerdict() {
        ContentAutoReview existing = ContentAutoReview.builder()
                .id(UUID.randomUUID())
                .target(AutoApprovalTarget.PROBLEM)
                .contentId(CONTENT)
                .authorId(AUTHOR)
                .title("The version that was held")
                .approved(false)
                .hold(AutoApprovalHold.UNCLEAR)
                .reason("Too little to go on")
                .checkedAt(LocalDateTime.now().minusHours(1))
                .build();
        when(reviewRepository.findByTargetAndContentId(
                AutoApprovalTarget.PROBLEM,
                CONTENT
        )).thenReturn(Optional.of(existing));

        recorder.record(
                event(AutoApprovalTarget.PROBLEM, "The version that was fixed"),
                AutoApprovalDecision.approve("Safe and on topic")
        );

        ContentAutoReview saved = captureSaved();
        assertSame(existing, saved);
        assertEquals("The version that was fixed", saved.getTitle());
        assertTrue(saved.isApproved());
        assertNull(saved.getHold());
        assertEquals("Safe and on topic", saved.getReason());
    }

    /**
     * A showcase title has no length limit of its own, and the column holds 255.
     */
    @Test
    void anOverlongTitleIsClippedToTheColumn() {
        when(reviewRepository.findByTargetAndContentId(
                AutoApprovalTarget.SHOWCASE,
                CONTENT
        )).thenReturn(Optional.empty());

        recorder.record(
                event(AutoApprovalTarget.SHOWCASE, "x".repeat(400)),
                AutoApprovalDecision.hold(AutoApprovalHold.UNCLEAR, "Vague")
        );

        assertEquals(255, captureSaved().getTitle().length());
    }

    private ContentAutoReview captureSaved() {
        ArgumentCaptor<ContentAutoReview> saved =
                ArgumentCaptor.forClass(ContentAutoReview.class);
        verify(reviewRepository).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    private ContentSubmittedEvent event(
            AutoApprovalTarget target,
            String title
    ) {
        return new ContentSubmittedEvent(
                target,
                CONTENT,
                AUTHOR,
                title,
                "prose"
        );
    }
}
