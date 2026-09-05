package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.dto.ContentAutoReviewResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "Why is my post still pending?", answered.
 *
 * <p>A separate endpoint rather than a field on the post itself. A problem and a
 * showcase are both served from caches that are shared between viewers, and the
 * only person who may read the check's reasoning is the author — a per-viewer
 * field on a shared cached response is how one author ends up reading another's
 * verdict.
 */
@RestController
@RequestMapping("/api/v1/me/auto-reviews")
@RequiredArgsConstructor
public class ContentAutoReviewController {

    private final ContentAutoReviewService contentAutoReviewService;

    /**
     * @param target   narrows to problems or to showcases; omitted, both
     * @param approved {@code false} is the list worth showing an author: their
     *                 posts the check did not publish, each with the sentence
     *                 saying why
     */
    @GetMapping
    public Page<ContentAutoReviewResponse> findMine(
            @RequestParam(required = false) AutoApprovalTarget target,
            @RequestParam(required = false) Boolean approved,
            @PageableDefault(
                    size = 20,
                    sort = "checkedAt",
                    direction = Sort.Direction.DESC
            )
            @ParameterObject
            Pageable pageable
    ) {
        return contentAutoReviewService.findMine(target, approved, pageable);
    }

    /**
     * The verdict on one post, for the page that is showing it as pending.
     *
     * <p>404 when the post was never checked automatically, which is the honest
     * answer: there is no verdict to show, and the post is waiting on a
     * moderator the way everything did before this feature existed.
     */
    @GetMapping("/{target}/{contentId}")
    public ContentAutoReviewResponse findOne(
            @PathVariable AutoApprovalTarget target,
            @PathVariable UUID contentId
    ) {
        return contentAutoReviewService.findOne(target, contentId);
    }
}
