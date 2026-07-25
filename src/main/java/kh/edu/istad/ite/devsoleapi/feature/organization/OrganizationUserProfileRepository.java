package kh.edu.istad.ite.devsoleapi.feature.organization;

import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationUserProfileRepository
        extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByEmailIgnoreCase(String email);
}
