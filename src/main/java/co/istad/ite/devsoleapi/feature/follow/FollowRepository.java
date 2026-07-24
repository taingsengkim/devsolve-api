package co.istad.ite.devsoleapi.feature.follow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, String> {

    // Find specific follow relationship
    Optional<Follow> findByFollowerIdAndFollowableTypeAndFollowableId(
            String followerId,
            String followableType,
            String followableId);

    // Check if relationship exists
    boolean existsByFollowerIdAndFollowableTypeAndFollowableId(
            String followerId,
            String followableType,
            String followableId);

    // Get all follows by follower
    List<Follow> findByFollowerId(String followerId);

    // Get all follows by followable
    List<Follow> findByFollowableTypeAndFollowableId(String followableType, String followableId);

    // Count followers
    long countByFollowableTypeAndFollowableId(String followableType, String followableId);

    // Count following
    long countByFollowerId(String followerId);

    // Check if user is following any entity of a type
    boolean existsByFollowerIdAndFollowableType(String followerId, String followableType);

    // Custom query to get follows with pagination
    @Query("SELECT f FROM Follow f WHERE f.follower.id = :followerId ORDER BY f.createdAt DESC")
    List<Follow> findRecentFollowsByFollower(@Param("followerId") String followerId);

    // Delete all follows for a user (useful for account deletion)
    void deleteByFollowerId(String followerId);

    void deleteByFollowableTypeAndFollowableId(String followableType, String followableId);
}
