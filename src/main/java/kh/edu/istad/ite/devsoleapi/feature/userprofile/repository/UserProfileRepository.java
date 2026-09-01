package kh.edu.istad.ite.devsoleapi.feature.userprofile.repository;

import kh.edu.istad.ite.devsoleapi.feature.reports.enums.ReportState;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    java.util.Optional<UserProfile> findByUsernameIgnoreCaseAndStatus(
            String username,
            UserStatus status
    );

    boolean existsByUsernameIgnoreCase(String username);


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

    /**
     * Adds the points on a reward to a researcher's standing.
     *
     * <p>Separate from {@link #applyRecognition} because a reward is not a
     * recognition: it moves reputation but must not move recognitionCount or
     * criticalReports, which count recognitions.
     *
     * <p>The same single-UPDATE reasoning applies — two payouts recorded at
     * the same moment would otherwise read the same old total and the second
     * write would erase the first.
     *
     * @return rows updated — zero means the profile disappeared mid-award
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserProfile profile
               set profile.reputation = profile.reputation + :points
             where profile.id = :userId
            """)
    int applyRewardPoints(
            @Param("userId") UUID userId,
            @Param("points") int points
    );

    /**
     * Recomputes one researcher's report counters from the reports themselves.
     *
     * <p>Recomputed rather than incremented, unlike {@link #applyRecognition}
     * above. A recognition happens once and only ever adds; a report's state
     * moves back and forth — confirmed, then rejected, then confirmed again on
     * appeal — and a counter nudged on each transition drifts away from the
     * truth and never comes back. Counting is exact whenever it runs, and
     * running it twice is harmless.
     *
     * <p>{@code validReports} is the findings that were agreed to be real:
     * confirmed or resolved. NEW, TRIAGING and NEEDS_MORE_INFO have not been
     * decided yet, and REJECTED and DUPLICATE were decided against.
     *
     * @return rows updated — zero means the profile no longer exists
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserProfile profile
               set profile.totalReports = (
                       select count(report)
                         from Report report
                        where report.reporter.id = :userId
                   ),
                   profile.validReports = (
                       select count(report)
                         from Report report
                        where report.reporter.id = :userId
                          and report.state in :validStates
                   )
             where profile.id = :userId
            """)
    int refreshReportCounts(
            @Param("userId") UUID userId,
            @Param("validStates") Collection<ReportState> validStates
    );
}
