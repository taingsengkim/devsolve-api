package kh.edu.istad.ite.devsoleapi.feature.userprofile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

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
                    :query is null
                    or lower(profile.fullName)
                        like concat('%', :query, '%')
                    or lower(profile.email)
                        like concat('%', :query, '%')
              )
            """)
    Page<UserProfile> findForAdmin(
            @Param("query") String query,
            @Param("status") UserStatus status,
            Pageable pageable
    );

    @Query("""
            select profile
            from UserProfile profile
            where profile.status = :activeStatus
              and (
                    :query is null
                    or lower(profile.fullName)
                        like concat('%', :query, '%')
                    or lower(profile.country)
                        like concat('%', :query, '%')
              )
            """)
    Page<UserProfile> findPublicProfiles(
            @Param("query") String query,
            @Param("activeStatus") UserStatus activeStatus,
            Pageable pageable
    );

    java.util.Optional<UserProfile> findByIdAndStatus(
            UUID id,
            UserStatus status
    );
    Page<UserProfile> findAllByOrderByReputationDesc(Pageable pageable);
}
