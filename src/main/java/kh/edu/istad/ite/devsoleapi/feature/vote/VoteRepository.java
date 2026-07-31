package kh.edu.istad.ite.devsoleapi.feature.vote;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
