package co.istad.ite.devsoleapi.feature.follow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    // Find specific follow relationship
    Optional<Follow> findByFollowerIdAndFollowableTypeAndFollowableId(
            UUID followerId,
            String followableType,
            UUID followableId);

    // Check if relationship exists
    boolean existsByFollowerIdAndFollowableTypeAndFollowableId(
            UUID followerId,
            String followableType,
            UUID followableId);

    // Get all follows by follower
    List<Follow> findByFollowerId(UUID followerId);

    // Get all follows by followable
    List<Follow> findByFollowableTypeAndFollowableId(String followableType, UUID followableId);

    // Count followers
    long countByFollowableTypeAndFollowableId(String followableType, UUID followableId);

    // Count following
    long countByFollowerId(UUID followerId);

    // Check if user is following any entity of a type
    boolean existsByFollowerIdAndFollowableType(UUID followerId, String followableType);

    // Custom query to get follows with pagination
    @Query("SELECT f FROM Follow f WHERE f.follower.id = :followerId ORDER BY f.createdAt DESC")
    List<Follow> findRecentFollowsByFollower(@Param("followerId") UUID followerId);

    // Delete all follows for a user (useful for account deletion)
    void deleteByFollowerId(UUID followerId);

    void deleteByFollowableTypeAndFollowableId(String followableType, UUID followableId);
}
