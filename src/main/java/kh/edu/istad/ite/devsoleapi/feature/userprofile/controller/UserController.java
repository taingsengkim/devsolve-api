package kh.edu.istad.ite.devsoleapi.feature.userprofile.controller;


import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UpdateUserProfileRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.PublicUserProfileResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UsernameAvailabilityResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.service.UserProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/user-profiles")
@RequiredArgsConstructor
public class UserController {
    private final UserProfileService userProfileService;

    @GetMapping
    public Page<PublicUserProfileResponse> getPublicProfiles(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "pageNumber must be >= 0")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "pageSize must be >= 1")
            @Max(value = 100, message = "pageSize must be <= 100")
            int pageSize
    ) {
        return userProfileService.getPublicProfiles(
                query,
                pageNumber,
                pageSize
        );
    }

    @GetMapping("/{userId}")
    public PublicUserProfileResponse getPublicProfile(
            @PathVariable UUID userId
    ) {
        return userProfileService.getPublicProfile(userId);
    }

    /**
     * The same profile as /{userId}, addressed the way a person shares it.
     * Kept on its own path rather than overloading /{userId}: a handle and a
     * UUID in one segment means a non-UUID either 400s before any lookup or
     * silently resolves to somebody else.
     */
    @GetMapping("/by-username/{username}")
    public PublicUserProfileResponse getPublicProfileByUsername(
            @PathVariable String username
    ) {
        return userProfileService.getPublicProfileByUsername(username);
    }

    @GetMapping("/me")
    public UserProfileResponse me(){
        return userProfileService.me();
    }

    /**
     * Answers while the user is still typing, so a taken or reserved handle is
     * refused beside the field instead of on submit.
     */
    @GetMapping("/me/username-available")
    public UsernameAvailabilityResponse checkUsernameAvailability(
            @RequestParam String username
    ) {
        return userProfileService.checkUsernameAvailability(username);
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(@Valid @RequestBody UpdateUserProfileRequest request) {
        return userProfileService.updateMe(request);
    }

    @PutMapping(
            value = "/me/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public UserProfileResponse uploadAvatar(
            @RequestPart("file") MultipartFile file
    ) {
        return userProfileService.uploadAvatar(file);
    }

    @DeleteMapping("/me/avatar")
    public UserProfileResponse removeAvatar() {
        return userProfileService.removeAvatar();
    }

    @GetMapping("/{userId}/reputation")
    public Integer getReputation(
            @PathVariable UUID userId
    ) {
        return userProfileService.getReputation(userId);
    }

}
