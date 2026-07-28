package kh.edu.istad.ite.devsoleapi.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        http.oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
        ));

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/register").permitAll()
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/organizations/register"
                ).permitAll()
                .requestMatchers(
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/scalar/**"
                ).permitAll()

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/problems/mine"
                ).authenticated()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/categories/**"
                ).permitAll()
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/categories"
                ).hasRole("ADMIN")
                .requestMatchers(
                        HttpMethod.DELETE,
                        "/api/v1/categories/**"
                ).hasRole("ADMIN")
                .requestMatchers(
                        HttpMethod.PATCH,
                        "/api/v1/categories/**"
                ).hasRole("ADMIN")

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/problems",
                        "/api/v1/problems/*",
                        "/api/v1/problems/*/attachments/*/download"
                ).permitAll()
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/problems/*/views"
                ).permitAll()
                .requestMatchers(
                        "/api/v1/admin/problems",
                        "/api/v1/admin/problems/**"
                ).hasRole("ADMIN")
                .requestMatchers("/api/v1/problems/**").authenticated()

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/programs",
                        "/api/v1/programs/*",
                        "/api/v1/programs/handle/*",
                        "/api/v1/programs/*/updates"
                ).permitAll()
                .requestMatchers(
                        "/api/v1/admin/programs",
                        "/api/v1/admin/programs/**"
                ).hasRole("ADMIN")
                .requestMatchers(
                        "/api/v1/programs/**"
                ).authenticated()

                .requestMatchers(
                        "/api/v1/organizations/me",
                        "/api/v1/organizations/me/**",
                        "/api/v1/organizations/invitations/**"
                ).authenticated()
                .requestMatchers(
                        HttpMethod.PATCH,
                        "/api/v1/organizations/*/approve",
                        "/api/v1/organizations/*/reject"
                ).hasRole("ADMIN")
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/organizations/slug/**",
                        "/api/v1/organizations/*"
                ).permitAll()
                .requestMatchers("/api/v1/organizations/**").authenticated()

                .anyRequest().authenticated()
        );

        http.sessionManagement(state -> state
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );
        http.cors(Customizer.withDefaults());
        http.csrf(AbstractHttpConfigurer::disable);
        http.formLogin(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object realmAccessClaim = jwt.getClaim("realm_access");
            if (!(realmAccessClaim instanceof Map<?, ?> realmAccess)) {
                return List.of();
            }

            Object rolesClaim = realmAccess.get("roles");
            if (!(rolesClaim instanceof Collection<?> roles)) {
                return List.of();
            }

            return roles.stream()
                    .map(String::valueOf)
                    .map(role -> role.toUpperCase(Locale.ROOT))
                    .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(
                            "ROLE_" + role
                    ))
                    .toList();
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:3000}")
            List<String> allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                allowedOrigins.stream()
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .toList()
        );
        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept"
        ));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
