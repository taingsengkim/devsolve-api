package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.CreateFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagGroupResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagQueueSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.ResolveFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.TargetFlagActionResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ContentFlagService {
    FlagResponse createFlag(
            CreateFlagRequest request
    );

    Page<FlagResponse> getMyFlags(
            FlagStatus status,
            int pageNumber,
            int pageSize
    );

    /**
     * The moderation queue, one row per report.
     *
     * @param search free text over the reported writing, its author, the person
     *               who reported it and the note they left. Blank or null
     *               searches everything
     * @param sort   defaults to {@link FlagQueueSort#NEWEST}
     */
    Page<FlagResponse> getAdminFlags(
            FlagStatus status,
            FlaggableType flaggableType,
            FlagReason reason,
            String search,
            FlagQueueSort sort,
            int pageNumber,
            int pageSize
    );

    /**
     * The same queue, one card per reported thing rather than per report.
     *
     * <p>The filters select which content appears; the counts on each card are
     * of every report on it. See {@link FlagGroupResponse}.
     */
    Page<FlagGroupResponse> getAdminFlagGroups(
            FlagStatus status,
            FlaggableType flaggableType,
            FlagReason reason,
            String search,
            FlagQueueSort sort,
            int pageNumber,
            int pageSize
    );

    /** The badge counts above the queue. */
    FlagQueueSummaryResponse getQueueSummary();

    FlagResponse getAdminFlagById(UUID id);

    FlagResponse dismissFlag(
            UUID id
    );

    FlagResponse resolveFlag(
            UUID id,
            ResolveFlagRequest request
    );

    /**
     * Upholds every open report on one piece of content at once, and takes the
     * content down if asked.
     *
     * <p>The action behind a card in the grouped queue: ten reports about the
     * same post are one decision, and closing them one at a time is ten chances
     * to lose track of which are done — or to take the same post down twice.
     */
    TargetFlagActionResponse resolveTargetFlags(
            FlaggableType flaggableType,
            UUID flaggableId,
            ResolveFlagRequest request
    );

    /** Dismisses every open report on one piece of content at once. */
    TargetFlagActionResponse dismissTargetFlags(
            FlaggableType flaggableType,
            UUID flaggableId
    );
}
