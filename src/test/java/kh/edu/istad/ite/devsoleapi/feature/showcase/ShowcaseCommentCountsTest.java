package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowcaseCommentCountsTest {

    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private ShowcaseCommentCounts commentCounts;

    @Test
    void aWholePageOfCountsCostsOneQuery() {
        UUID discussed = UUID.randomUUID();
        UUID quiet = UUID.randomUUID();

        when(commentRepository.countAllByCommentableIds(
                CommentableType.SHOWCASE,
                List.of(discussed, quiet)
        )).thenReturn(List.of(count(discussed, 7L)));

        Page<ShowCasesSummaryResponse> result =
                commentCounts.applyToSummaries(new PageImpl<>(List.of(
                        summary(discussed),
                        summary(quiet)
                )));

        assertEquals(7L, result.getContent().get(0).commentCount());
        assertEquals(
                0L,
                result.getContent().get(1).commentCount(),
                "a showcase absent from the aggregate has no comments, not "
                        + "an unknown number of them"
        );
        verify(commentRepository).countAllByCommentableIds(
                CommentableType.SHOWCASE,
                List.of(discussed, quiet)
        );
    }

    @Test
    void anEmptyPageDoesNotQueryAtAll() {
        commentCounts.applyToDetails(new PageImpl<>(List.of()));

        verify(commentRepository, never()).countAllByCommentableIds(
                eq(CommentableType.SHOWCASE),
                anyList()
        );
    }

    @Test
    void aSingleShowcaseIsCountedDirectly() {
        UUID showcaseId = UUID.randomUUID();
        when(commentRepository.countVisible(
                CommentableType.SHOWCASE,
                showcaseId
        )).thenReturn(3L);

        ShowCasesResponse result = commentCounts.applyToDetail(
                ShowCasesResponse.builder().id(showcaseId).build()
        );

        assertEquals(3L, result.commentCount());
    }

    private ShowCasesSummaryResponse summary(UUID id) {
        return ShowCasesSummaryResponse.builder().id(id).build();
    }

    private IdCountProjection count(UUID id, long total) {
        return new IdCountProjection() {
            @Override
            public UUID getId() {
                return id;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
