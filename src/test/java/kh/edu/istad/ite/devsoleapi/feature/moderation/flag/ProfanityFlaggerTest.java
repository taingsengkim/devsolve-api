package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfanityFlaggerTest {

    @Mock
    private ContentFlagRepository contentFlagRepository;

    @InjectMocks
    private ProfanityFlagger flagger;

    @Test
    void cleanContentRaisesNothing() {
        flagger.review(
                FlaggableType.COMMENT,
                UUID.randomUUID(),
                "The retry logic looks correct to me."
        );
        verify(contentFlagRepository, never()).save(any());
    }

    @Test
    void profanityRaisesAPendingFlagWithNoReporter() {
        UUID commentId = UUID.randomUUID();
        when(contentFlagRepository
                .existsBySourceAndFlaggableTypeAndFlaggableId(
                        eq(FlagSource.AUTOMATED),
                        eq(FlaggableType.COMMENT),
                        eq(commentId)
                ))
                .thenReturn(false);

        flagger.review(FlaggableType.COMMENT, commentId, "this is shit");

        ArgumentCaptor<ContentFlag> captor =
                ArgumentCaptor.forClass(ContentFlag.class);
        verify(contentFlagRepository).save(captor.capture());

        ContentFlag flag = captor.getValue();
        assertEquals(FlagSource.AUTOMATED, flag.getSource());
        assertEquals(FlagStatus.PENDING, flag.getStatus());
        assertEquals(FlagReason.OFFENSIVE, flag.getReason());
        assertEquals(FlaggableType.COMMENT, flag.getFlaggableType());
        assertEquals(commentId, flag.getFlaggableId());
        assertNull(flag.getReporter());
        // The moderator needs to know which word tripped it, or a false
        // positive costs them a read of the whole comment.
        assertTrue(flag.getDescription().contains("shit"));
    }

    @Test
    void contentAlreadyFlaggedIsNotFlaggedAgain() {
        UUID commentId = UUID.randomUUID();
        when(contentFlagRepository
                .existsBySourceAndFlaggableTypeAndFlaggableId(
                        eq(FlagSource.AUTOMATED),
                        eq(FlaggableType.COMMENT),
                        eq(commentId)
                ))
                .thenReturn(true);

        flagger.review(FlaggableType.COMMENT, commentId, "this is shit");

        verify(contentFlagRepository, never()).save(any());
    }

    @Test
    void nullFieldsAreSkippedRatherThanScanned() {
        flagger.review(
                FlaggableType.PROBLEM,
                UUID.randomUUID(),
                "A title",
                null,
                null
        );
        verify(contentFlagRepository, never()).save(any());
    }
}
