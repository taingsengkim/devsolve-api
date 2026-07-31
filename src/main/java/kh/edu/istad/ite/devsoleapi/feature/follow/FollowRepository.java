package kh.edu.istad.ite.devsoleapi.feature.follow;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    Optional<Follow> findByFollower_IdAndFollowableTypeAndFollowableId(
            UUID followerId,
            FollowType followableType,
            UUID followableId
    );

    boolean existsByFollower_IdAndFollowableTypeAndFollowableId(
            UUID followerId,
            FollowType followableType,
            UUID followableId
    );

    long deleteByFollower_IdAndFollowableTypeAndFollowableId(
            UUID followerId,
            FollowType followableType,
            UUID followableId
    );

    long countByFollowableTypeAndFollowableId(
            FollowType followableType,
            UUID followableId
    );

    @Query("""
            select follow
            from Follow follow
            where follow.follower.id = :followerId
              and (
                    :followableType is null
                    or follow.followableType = :followableType
              )
            """)
    Page<Follow> findFollowing(
            @Param("followerId") UUID followerId,
            @Param("followableType") FollowType followableType,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "follower")
    Page<Follow> findByFollowableTypeAndFollowableId(
            FollowType followableType,
            UUID followableId,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO public.follows (
                id,
                follower_id,
                followable_type,
                followable_id,
                created_at
            )
            VALUES (
                :id,
                :followerId,
                :followableType,
                :followableId,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (
                follower_id,
                followable_type,
                followable_id
            ) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("followerId") UUID followerId,
            @Param("followableType") String followableType,
            @Param("followableId") UUID followableId
    );
}
