package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads the moderation queue. The SQL lives in {@link FlagQueueQueries}.
 *
 * <p>Separate from {@link ContentFlagRepository}, which stays the write side:
 * these are projections of a flag joined to content that no entity relates it
 * to, and returning them from the same interface that saves and deletes flags
 * would invite somebody to mistake a row here for something they can modify.
 *
 * <p>Extends the bare {@link Repository} marker so it offers these reads and
 * nothing else.
 */
public interface FlagQueueRepository extends Repository<ContentFlag, UUID> {

    /**
     * One page of the queue.
     *
     * <p>Every filter is a string, including the enums and the reporter's ID.
     * A null argument means "do not filter", and the driver cannot bind an
     * untyped null — passing the values as text lets one signature carry both
     * the value and its absence, with the SQL casting back.
     *
     * @param search a pre-wrapped {@code %term%}, or null for no search
     * @param sort   the name of a {@link FlagQueueSort}; never null
     */
    @Query(
            value = FlagQueueQueries.SEARCH_FLAGS,
            countQuery = FlagQueueQueries.COUNT_FLAGS,
            nativeQuery = true
    )
    Page<FlagRow> search(
            @Param("status") String status,
            @Param("flaggableType") String flaggableType,
            @Param("reason") String reason,
            @Param("reporterId") String reporterId,
            @Param("search") String search,
            @Param("sort") String sort,
            Pageable pageable
    );

    /** One flag, carrying the same resolved content the listing carries. */
    @Query(value = FlagQueueQueries.FIND_FLAG, nativeQuery = true)
    Optional<FlagRow> findRow(@Param("id") String id);

    /** One card per reported thing, however many reports it has drawn. */
    @Query(
            value = FlagQueueQueries.GROUP_FLAGS,
            countQuery = FlagQueueQueries.COUNT_GROUPS,
            nativeQuery = true
    )
    Page<FlagGroupRow> searchGroups(
            @Param("status") String status,
            @Param("flaggableType") String flaggableType,
            @Param("reason") String reason,
            @Param("reporterId") String reporterId,
            @Param("search") String search,
            @Param("sort") String sort,
            Pageable pageable
    );

    /** Every flag counted once, by status, reason and kind at the same time. */
    @Query(value = FlagQueueQueries.TALLY_FLAGS, nativeQuery = true)
    List<FlagTally> tally();

    /**
     * What was reported, resolved from whichever of the five tables holds it.
     *
     * <p>All null when the row is gone outright rather than soft-deleted.
     * {@code targetCreatedAt} is the reliable tell: it is NOT NULL in all five
     * tables, while a comment legitimately has no title and only a showcase has
     * a thumbnail.
     */
    interface FlagTargetRow {

        /** Null for a comment, which has no title of its own. */
        String getTargetTitle();

        /** The full body. Trimmed to a snippet by the caller, not by the SQL,
         *  so that a search still matches text past the end of the excerpt. */
        String getTargetBody();

        /** The kind's own status word, before it is normalised. */
        String getTargetState();

        Boolean getTargetDeleted();

        LocalDateTime getTargetCreatedAt();

        String getTargetThumbnailUrl();

        /** A program's handle. Null for everything else. */
        String getTargetSlug();

        /** The organization rather than a person, for a program. */
        UUID getTargetAuthorId();

        String getTargetAuthorName();

        String getTargetAuthorAvatarUrl();

        /** What a comment or solution hangs off, for building a link to it. */
        String getTargetParentType();

        UUID getTargetParentId();
    }

    /**
     * Every flag on the same content, whatever the caller filtered by.
     * See {@link FlagQueueQueries}.
     */
    interface FlagTallyRow {

        long getReportCount();

        long getPendingCount();

        /** Comma-joined {@link FlagReason} names, or null if there are none. */
        String getReasons();

        Boolean getAutomated();

        LocalDateTime getFirstReportedAt();

        LocalDateTime getLastReportedAt();

        UUID getLatestFlagId();
    }

    /** One row of the flat queue: the flag, its reporter and its target. */
    interface FlagRow extends FlagTargetRow, FlagTallyRow {

        UUID getId();

        String getSource();

        String getFlaggableType();

        UUID getFlaggableId();

        String getReason();

        String getDescription();

        String getStatus();

        UUID getReviewedBy();

        LocalDateTime getReviewedAt();

        String getResolutionNote();

        LocalDateTime getCreatedAt();

        /** Null when the content filter raised the flag rather than a person. */
        UUID getReporterId();

        String getReporterName();

        String getReporterAvatarUrl();

        Integer getReporterReputation();
    }

    /** One card of the grouped queue: the target and everything raised on it. */
    interface FlagGroupRow extends FlagTargetRow, FlagTallyRow {

        String getFlaggableType();

        UUID getFlaggableId();
    }

    /** One cell of the badge counts. */
    interface FlagTally {

        String getStatus();

        String getReason();

        String getFlaggableType();

        long getTotal();
    }
}
