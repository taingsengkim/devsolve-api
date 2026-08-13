package kh.edu.istad.ite.devsoleapi.config.security;

import kh.edu.istad.ite.devsoleapi.feature.auth.UserProvisioningService;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.AccountStatusService;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurity(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            CorsConfigurationSource corsConfigurationSource,
            AccountStatusService accountStatusService,
            UserProvisioningService userProvisioningService,
            ObjectMapper objectMapper
    ) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource));
        http.oauth2ResourceServer(oauth -> oauth.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
        ));

        // Social logins are created by Keycloak's broker flow and so never hit
        // /api/v1/auth/register: this gives them the local profile row that
        // registration would have written. Placed first of the two so the
        // status gate below sees a real row instead of its "no profile,
        // nothing to enforce" default.
        http.addFilterAfter(
                new UserProvisioningFilter(userProvisioningService),
                BearerTokenAuthenticationFilter.class
        );

        // Runs once the bearer token has been turned into an Authentication, so
        // a suspended or removed account is stopped before it reaches any
        // controller. Constructed here rather than injected as a bean to keep
        // the servlet container from registering it a second time.
        http.addFilterAfter(
                new AccountStatusFilter(accountStatusService, objectMapper),
                UserProvisioningFilter.class
        );
//        http.cors(Customizer.withDefaults());
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/register").permitAll()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/hacktivity",
                        "/api/v1/user-profiles/*/hacktivity"
                ).permitAll()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/hacktivity/mine"
                ).authenticated()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/organizations/me/hacktivity"
                ).authenticated()

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/organizations/*/hacktivity"
                ).permitAll()
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
                        "/api/v1/user-profiles/*/reputation",
                        "/api/v1/user-profiles/*/reports"
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
                        HttpMethod.GET,
                        "/api/v1/categories"
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
                        HttpMethod.PUT,
                        "/api/v1/categories/**"
                ).hasRole("ADMIN")

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/problems",
                        "/api/v1/problems/*",
                        "/api/v1/problems/*/solutions",
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
                        "/api/v1/solutions/mine"
                ).authenticated()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/solutions/*"
                ).permitAll()
                .requestMatchers(
                        "/api/v1/solutions/**"
                ).authenticated()

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/comments/mine"
                ).authenticated()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/comments",
                        "/api/v1/comments/*"
                ).permitAll()
                .requestMatchers(
                        "/api/v1/comments/**"
                ).authenticated()

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/votes/mine"
                ).authenticated()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/votes/*/*/summary"
                ).permitAll()
                .requestMatchers(
                        "/api/v1/votes/**"
                ).authenticated()

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/follows/mine"
                ).authenticated()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/follows/*/*/summary",
                        "/api/v1/follows/*/*/followers",
                        "/api/v1/follows/users/*/following"
                ).permitAll()
                .requestMatchers(
                        "/api/v1/follows/**",
                        "/api/v1/notifications/**"
                ).authenticated()
                .requestMatchers(
                        "/api/v1/bookmarks/**"
                ).authenticated()

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/user-profiles/me"
                ).authenticated()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/user-profiles",
                        "/api/v1/user-profiles/*"
                ).permitAll()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/user-profiles/*/problems",
                        "/api/v1/user-profiles/*/solutions",
                        "/api/v1/user-profiles/*/showcases"
                ).permitAll()
                .requestMatchers(
                        "/api/v1/user-profiles/**"
                ).authenticated()

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
                        HttpMethod.GET,
                        "/api/v1/organizations/slug/**",
                        "/api/v1/organizations/*"
                ).permitAll()
                .requestMatchers("/api/v1/organizations/**").authenticated()
                // Admin flag moderation endpoints
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/admin/flags"
                ).hasRole("ADMIN")

                .requestMatchers(
                        HttpMethod.PATCH,
                        "/api/v1/admin/flags/*/dismiss"
                ).hasRole("ADMIN")

                // Admin moderation action endpoint
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/admin/*/moderation-actions"
                ).hasRole("ADMIN")

                // Protect any other admin endpoint
                .requestMatchers(
                        "/api/v1/admin/**"
                ).hasRole("ADMIN")

                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/showcases/mine"
                ).authenticated()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/showcases",
                        "/api/v1/showcases/*"
                ).permitAll()
                .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/showcases/*/views"
                ).permitAll()
                .requestMatchers(
                        "/api/v1/reputation/leaderboard"
                ).permitAll()
                .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/users/*/recognitions"
                ).permitAll()

                .anyRequest().authenticated()
        );

        http.sessionManagement(state -> state
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );
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
            @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173,https://devsolve-api.quizzy.it.com}")
            List<String> allowedOrigins
    ) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(
                allowedOrigins.stream()
                        .map(String::trim)
                        .filter(origin -> !origin.isBlank())
                        .toList()
        );
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "Origin"
        ));
        config.setExposedHeaders(List.of("Authorization", "Location"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

}
