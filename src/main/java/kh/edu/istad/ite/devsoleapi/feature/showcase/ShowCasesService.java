package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.CreateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewQueueItemResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewDetailResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseReviewHistoryResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseViewCountResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.UpdateShowcaseStatusRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ShowCasesService {
    Page<ShowCasesResponse> getAllPublished(
            String query,
            UUID categoryId,
            String sortBy,
            String sortDirection,
            int pageNumber,
            int pageSize
    );

    Page<ShowCasesSummaryResponse> getMyShowcases(
            int pageNumber,
            int pageSize
    );

    Page<ShowCasesSummaryResponse> getPublishedByAuthor(
            UUID authorId,
            int pageNumber,
            int pageSize
    );

    Page<ShowcaseReviewQueueItemResponse> getReviewQueue(
            ReviewStatus reviewStatus,
            int pageNumber,
            int pageSize
    );

    ShowcaseReviewDetailResponse getReviewDetail(UUID showcaseId);

    ShowcaseReviewDetailResponse getMyRevision(UUID showcaseId);

    Page<ShowcaseReviewHistoryResponse> getReviewHistory(
            UUID showcaseId,
            int pageNumber,
            int pageSize
    );

    ShowCasesResponse getById(UUID id);
//
    ShowCasesResponse create(CreateShowCasesRequest request);
//
    ShowCasesResponse update(UUID showcaseId,
                             UpdateShowCasesRequest request);

    void softDelete(UUID showcaseId);
//
    void hardDelete(UUID showcaseId);

    void cancelRevision(UUID showcaseId);

    void restore(UUID showcaseId);

    ShowcaseViewCountResponse incrementViewCount(UUID showcaseId);

    ShowCasesResponse updateStatus(
            UUID showcaseId,
            UpdateShowcaseStatusRequest request
    );

}



