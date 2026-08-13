package kh.edu.istad.ite.devsoleapi.feature.userprofile.repository;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    @Query("""
            select profile
            from UserProfile profile
            where (
                    :status is null
                    or profile.status = :status
            )
              and (
                    lower(profile.fullName)
                        like :queryPattern
                    or lower(profile.email)
                        like :queryPattern
              )
            """)
    Page<UserProfile> findForAdmin(
            @Param("queryPattern") String queryPattern,
            @Param("status") UserStatus status,
            Pageable pageable
    );

    @Query("""
            select profile
            from UserProfile profile
            where profile.status = :activeStatus
              and (
                    lower(profile.fullName)
                        like :queryPattern
                    or lower(profile.country)
                        like :queryPattern
              )
            """)
    Page<UserProfile> findPublicProfiles(
            @Param("queryPattern") String queryPattern,
            @Param("activeStatus") UserStatus activeStatus,
            Pageable pageable
    );

    Page<UserProfile> findAllByStatus(
            UserStatus status,
            Pageable pageable
    );

    java.util.Optional<UserProfile> findByIdAndStatus(
            UUID id,
            UserStatus status
    );


    @Query("""
            select count(profile) as totalUsers,
                   coalesce(sum(case when profile.status = :activeStatus
                       then 1 else 0 end), 0) as activeUsers,
                   coalesce(sum(case when profile.status = :suspendedStatus
                       then 1 else 0 end), 0) as suspendedUsers,
                   coalesce(sum(case when profile.status = :removedStatus
                       then 1 else 0 end), 0) as removedUsers
            from UserProfile profile
            """)
    AdminUserCounts findAdminCounts(
            @Param("activeStatus") UserStatus activeStatus,
            @Param("suspendedStatus") UserStatus suspendedStatus,
            @Param("removedStatus") UserStatus removedStatus
    );

    interface AdminUserCounts {

        long getTotalUsers();

        long getActiveUsers();

        long getSuspendedUsers();

        long getRemovedUsers();
    }

    /**
     * Leaderboard page. Reputation alone is not a unique sort key — everybody
     * starts tied on zero — and Postgres gives no ordering guarantee between
     * equal rows, so paging over it duplicated some profiles and skipped
     * others. The id breaks every tie, which makes the sequence stable across
     * pages. Suspended and removed accounts are filtered out rather than left
     * ranking against active researchers.
     */
    Page<UserProfile> findAllByStatusOrderByReputationDescIdAsc(
            UserStatus status,
            Pageable pageable
    );

    /**
     * Applies one recognition to a profile's standing.
     *
     * <p>Written as a single UPDATE rather than read-modify-write on a loaded
     * entity: two triagers awarding recognitions to the same researcher at the
     * same moment would otherwise both read the old total and the second write
     * would erase the first. The database does the arithmetic, so concurrent
     * awards add up.
     *
     * @return rows updated — zero means the profile disappeared mid-award
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserProfile profile
               set profile.reputation =
                       profile.reputation + :points,
                   profile.recognitionCount =
                       profile.recognitionCount + 1,
                   profile.criticalReports =
                       profile.criticalReports + :criticalDelta
             where profile.id = :userId
            """)
    int applyRecognition(
            @Param("userId") UUID userId,
            @Param("points") int points,
            @Param("criticalDelta") int criticalDelta
    );
}
