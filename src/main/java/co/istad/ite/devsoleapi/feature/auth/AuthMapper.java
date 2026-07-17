package co.istad.ite.devsoleapi.feature.auth;

import co.istad.ite.devsoleapi.feature.auth.dto.RegisterRequest;
import co.istad.ite.devsoleapi.feature.auth.dto.RegisterResponse;
import org.keycloak.representations.idm.UserRepresentation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public class AuthMapper {
    public RegisterResponse toRegisterResponse(RegisterRequest request, UserRepresentation userRepresentation){
        return RegisterResponse.builder ()
                .username(userRepresentation.getUsername())
                .userId(userRepresentation.getId())
                .phone(request.phone())
                .email(userRepresentation.getEmail())
                .firstName(userRepresentation.getFirstName())
                .lastName(userRepresentation.getLastName())
                .build();
    }
}
