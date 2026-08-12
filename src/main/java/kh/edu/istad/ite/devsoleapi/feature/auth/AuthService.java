package kh.edu.istad.ite.devsoleapi.feature.auth;

import kh.edu.istad.ite.devsoleapi.feature.auth.dto.RegisterRequest;
import kh.edu.istad.ite.devsoleapi.feature.auth.dto.RegisterResponse;
import kh.edu.istad.ite.devsoleapi.feature.auth.dto.SocialSyncResponse;

public interface AuthService {
    RegisterResponse  register(RegisterRequest registerRequest);

    SocialSyncResponse syncSocialAccount();
}
