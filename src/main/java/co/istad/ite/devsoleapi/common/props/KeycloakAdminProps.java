package co.istad.ite.devsoleapi.common.props;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "keycloak")
@Setter
@Getter
@NoArgsConstructor
public class KeycloakAdminProps {
    private String serverUrl;
    private String realm;
    private String targetRealm;
    private String clientId;
    private String clientSecret;
}
