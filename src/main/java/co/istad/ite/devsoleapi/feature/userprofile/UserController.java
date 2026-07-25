package co.istad.ite.devsoleapi.feature.userprofile;


import co.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import co.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user-profiles")
@RequiredArgsConstructor
public class UserController {
    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public UserProfileResponse me(){
        return userProfileService.me();
    }

    @GetMapping("/{id}/showcases")
    public Page<ShowCasesResponse> getUserShowCases(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return userProfileService.getUserShowCases(
                id,
                pageNumber,
                pageSize
        );
    }

}
