package kh.edu.istad.ite.devsoleapi.feature.userprofile.service;


import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.AdminUserSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.PublicUserProfileResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UsernameAvailabilityResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UserProfileService {
    UserProfileResponse me();

    UserProfileResponse updateMe(UpdateUserProfileRequest request);

    /** Replaces the caller's own avatar. */
    UserProfileResponse uploadAvatar(MultipartFile file);

    /** Clears the caller's own avatar. */
    UserProfileResponse removeAvatar();

    Page<AdminUserSummaryResponse> getAllForAdmin(
            String query,
            UserStatus status,
            int pageNumber,
            int pageSize
    );

    Page<PublicUserProfileResponse> getPublicProfiles(
            String query,
            int pageNumber,
            int pageSize
    );

    PublicUserProfileResponse getPublicProfile(UUID userId);

    /** The same public profile, addressed by handle instead of by id. */
    PublicUserProfileResponse getPublicProfileByUsername(String username);

    /**
     * Whether a handle could be taken right now, for a form that wants to say
     * so before the user submits.
     */
    UsernameAvailabilityResponse checkUsernameAvailability(String username);


    Integer getReputation(UUID userId);
}
