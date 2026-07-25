package co.istad.ite.devsoleapi.feature.userprofile;


import co.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import co.istad.ite.devsoleapi.feature.userprofile.dto.UpdateUserProfileRequest;

public interface UserProfileService {
    UserProfileResponse me();

    UserProfileResponse updateMe(UpdateUserProfileRequest request);
}
