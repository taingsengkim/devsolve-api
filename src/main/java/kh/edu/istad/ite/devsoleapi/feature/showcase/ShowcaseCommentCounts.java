package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fills the comment count in on showcase responses.
 *
 * <p>Problems have carried this since they were built; showcases never did, so
 * a card could not say how much discussion was on it and the only way to find
 * out was to fetch a page of comments per card. The count is read for a whole
 * page in one query, the same way the problem listing does it.
 *
 * <p>Applied after mapping rather than inside the mapper: the mapper is
 * reused by the review queue and the revision paths, where a count would be
 * both meaningless and an extra query.
 */
@Component
@RequiredArgsConstructor
class ShowcaseCommentCounts {

    private final CommentRepository commentRepository;

    Page<ShowCasesResponse> applyToDetails(Page<ShowCasesResponse> page) {
        if (page.isEmpty()) {
            return page;
        }
        Map<UUID, Long> counts = countsFor(
                page.getContent().stream()
                        .map(ShowCasesResponse::id)
                        .toList()
        );
        return page.map(response -> response.toBuilder()
                .commentCount(counts.getOrDefault(response.id(), 0L))
                .build());
    }

    Page<ShowCasesSummaryResponse> applyToSummaries(
            Page<ShowCasesSummaryResponse> page
    ) {
        if (page.isEmpty()) {
            return page;
        }
        Map<UUID, Long> counts = countsFor(
                page.getContent().stream()
                        .map(ShowCasesSummaryResponse::id)
                        .toList()
        );
        return page.map(response -> response.toBuilder()
                .commentCount(counts.getOrDefault(response.id(), 0L))
                .build());
    }

    ShowCasesResponse applyToDetail(ShowCasesResponse response) {
        return response.toBuilder()
                .commentCount(commentRepository.countVisible(
                        CommentableType.SHOWCASE,
                        response.id()
                ))
                .build();
    }

    private Map<UUID, Long> countsFor(List<UUID> showcaseIds) {
        return commentRepository
                .countAllByCommentableIds(
                        CommentableType.SHOWCASE,
                        showcaseIds
                )
                .stream()
                .collect(Collectors.toMap(
                        IdCountProjection::getId,
                        IdCountProjection::getTotal,
                        (first, second) -> first
                ));
    }
}
