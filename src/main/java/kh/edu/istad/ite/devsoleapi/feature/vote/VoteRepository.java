package kh.edu.istad.ite.devsoleapi.feature.vote;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    Optional<Vote> findByUserIdAndVotableTypeAndVotableId(
            UUID userId,
            VoteType votableType,
            UUID votableId
    );

    long deleteByUserIdAndVotableTypeAndVotableId(
            UUID userId,
            VoteType votableType,
            UUID votableId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO public.votes (
                id,
                user_id,
                votable_type,
                votable_id,
                vote_value,
                created_at
            )
            VALUES (
                :id,
                :userId,
                :votableType,
                :votableId,
                :voteValue,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (user_id, votable_type, votable_id)
            DO UPDATE SET vote_value = EXCLUDED.vote_value
            """, nativeQuery = true)
    int upsert(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("votableType") String votableType,
            @Param("votableId") UUID votableId,
            @Param("voteValue") short voteValue
    );

    @Query("""
            select
                coalesce(sum(vote.voteValue), 0) as score,
                coalesce(sum(
                    case when vote.voteValue = 1 then 1 else 0 end
                ), 0) as upvotes,
                coalesce(sum(
                    case when vote.voteValue = -1 then 1 else 0 end
                ), 0) as downvotes
            from Vote vote
            where vote.votableType = :votableType
              and vote.votableId = :votableId
            """)
    VoteSummaryProjection summarize(
            @Param("votableType") VoteType votableType,
            @Param("votableId") UUID votableId
    );

    /**
     * Vote scores for a whole page in one round trip. Ids with no votes are
     * simply absent from the result.
     */
    @Query("""
            select vote.votableId as id,
                   coalesce(sum(vote.voteValue), 0) as total
            from Vote vote
            where vote.votableType = :votableType
              and vote.votableId in :votableIds
            group by vote.votableId
            """)
    List<IdCountProjection> summarizeAll(
            @Param("votableType") VoteType votableType,
            @Param("votableIds") Collection<UUID> votableIds
    );

    /**
     * Like {@link #summarizeAll}, but carrying the up and down halves rather
     * than only the net score, for callers that render both.
     */
    @Query("""
            select vote.votableId as id,
                   coalesce(sum(vote.voteValue), 0) as score,
                   coalesce(sum(
                       case when vote.voteValue = 1 then 1 else 0 end
                   ), 0) as upvotes,
                   coalesce(sum(
                       case when vote.voteValue = -1 then 1 else 0 end
                   ), 0) as downvotes
            from Vote vote
            where vote.votableType = :votableType
              and vote.votableId in :votableIds
            group by vote.votableId
            """)
    List<VoteBreakdownProjection> summarizeAllDetailed(
            @Param("votableType") VoteType votableType,
            @Param("votableIds") Collection<UUID> votableIds
    );

    List<Vote> findAllByUserIdAndVotableTypeAndVotableIdIn(
            UUID userId,
            VoteType votableType,
            Collection<UUID> votableIds
    );

    @Query("""
            select vote
            from Vote vote
            where vote.userId = :userId
              and (
                    :votableType is null
                    or vote.votableType = :votableType
              )
            """)
    Page<Vote> findMine(
            @Param("userId") UUID userId,
            @Param("votableType") VoteType votableType,
            Pageable pageable
    );
}
