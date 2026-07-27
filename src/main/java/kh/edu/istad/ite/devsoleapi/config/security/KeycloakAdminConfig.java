package kh.edu.istad.ite.devsoleapi.config.security;

import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import lombok.RequiredArgsConstructor;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class KeycloakAdminConfig {
    private final KeycloakAdminProps props;
    @Bean
    public Keycloak keycloak() {
        return KeycloakBuilder.builder()
                .serverUrl(props.getServerUrl())
                .realm(props.getTargetRealm())
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(props.getClientId())
                .clientSecret(props.getClientSecret())
                .build();
    }
}