package co.istad.ite.devsoleapi.feature.auth;

import co.istad.ite.devsoleapi.feature.auth.dto.RegisterRequest;
import co.istad.ite.devsoleapi.feature.auth.dto.RegisterResponse;

public interface AuthService {
    RegisterResponse  register(RegisterRequest registerRequest);
}
