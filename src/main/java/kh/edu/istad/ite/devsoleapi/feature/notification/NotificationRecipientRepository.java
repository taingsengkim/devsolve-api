package kh.edu.istad.ite.devsoleapi.feature.notification;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * The address to send a notification to.
 *
 * <p>Its own narrow interface, and a projection rather than the entity: this
 * runs on a mail thread outside any request, where loading a full profile
 * would drag its associations along for two strings.
 */
public interface NotificationRecipientRepository
        extends JpaRepository<UserProfile, UUID> {

    @Query("""
            select profile.email as email,
                   profile.fullName as fullName,
                   profile.status as status
            from UserProfile profile
            where profile.id = :userId
            """)
    Optional<Recipient> findRecipientById(@Param("userId") UUID userId);

    interface Recipient {
        String getEmail();

        String getFullName();

        UserStatus getStatus();
    }
}
