package co.istad.ite.devsoleapi.feature.userprofile;


import co.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user-profiles")
@RequiredArgsConstructor
public class UserController {
    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public UserProfileResponse me(){
        return userProfileService.me();
    }

}
