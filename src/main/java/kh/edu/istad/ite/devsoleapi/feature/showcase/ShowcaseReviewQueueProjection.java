package kh.edu.istad.ite.devsoleapi.feature.showcase;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ShowcaseReviewQueueProjection {

    UUID getShowcaseId();

    UUID getRevisionId();

    String getSubmissionType();

    UUID getAuthorId();

    String getAuthorName();

    UUID getCategoryId();

    String getCategoryName();

    String getTitle();

    String getOverview();

    String getCoverImageUrl();

    String getLiveUrl();

    String getRepoUrl();

    String getVideoUrl();

    String getReviewStatus();

    LocalDateTime getSubmittedAt();
}
