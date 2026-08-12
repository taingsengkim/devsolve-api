package kh.edu.istad.ite.devsoleapi.feature.auth;

import kh.edu.istad.ite.devsoleapi.feature.auth.dto.RegisterRequest;
import kh.edu.istad.ite.devsoleapi.feature.auth.dto.RegisterResponse;
import kh.edu.istad.ite.devsoleapi.feature.auth.dto.SocialSyncResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final UserProfileService userProfileService;
    
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest registerRequest){
        return authService.register(registerRequest);
    }

    /**
     * Call once with the access token after an identity-provider sign-in.
     * Idempotent, so calling it on every social sign-in is safe: only the first
     * one reports {@code created}.
     */
    @PostMapping("/social/sync")
    public SocialSyncResponse syncSocialAccount() {
        return authService.syncSocialAccount();
    }

}
