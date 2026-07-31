package kh.edu.istad.ite.devsoleapi.feature.userprofile;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    Page<UserProfile> findAllByOrderByReputationDesc(Pageable pageable);
}
