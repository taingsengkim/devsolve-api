package kh.edu.istad.ite.devsoleapi.feature.userprofile;


import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.dto.UpdateUserProfileRequest;

public interface UserProfileService {
    UserProfileResponse me();

    UserProfileResponse updateMe(UpdateUserProfileRequest request);
}
