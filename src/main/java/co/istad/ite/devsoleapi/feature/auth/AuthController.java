package co.istad.ite.devsoleapi.feature.auth;

import co.istad.ite.devsoleapi.feature.auth.dto.RegisterRequest;
import co.istad.ite.devsoleapi.feature.auth.dto.RegisterResponse;
import co.istad.ite.devsoleapi.feature.userprofile.UserProfileService;
import co.istad.ite.devsoleapi.feature.userprofile.dto.UserProfileResponse;
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
    @GetMapping("/me")
    public UserProfileResponse me(){
        return userProfileService.me();
    }
}