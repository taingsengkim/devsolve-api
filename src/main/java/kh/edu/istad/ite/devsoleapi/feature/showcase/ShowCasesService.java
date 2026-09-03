package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.common.listing.ListingSort;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ShowCasesService {
    /**
     * The public feed. Returns the summary shape rather than the detail one:
     * a list never has steps to show, and sending the detail record meant
     * every card carried a {@code steps} field that was always null.
     */
    Page<ShowCasesSummaryResponse> getAllPublished(
            String query,
            UUID categoryId,
            String tag,
            ListingSort sort,
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

    ShowCasesResponse uploadCoverImage(
            UUID showcaseId,
            MultipartFile file
    );

    ShowCasesResponse removeCoverImage(UUID showcaseId);

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

    /**
     * Publishes a showcase the review model cleared. Answers false rather than
     * throwing when there is nothing left to publish.
     */
    boolean autoApprove(UUID showcaseId);

}



