package com.YemomBizGrow.common.security;

import com.YemomBizGrow.common.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * The single security filter chain for the whole API.
 *
 * <p>Key decisions:
 * <ul>
 *   <li><b>Stateless:</b> no HTTP session exists; identity = bearer access token,
 *       refresh = HttpOnly cookie. Sessions live only in the database
 *       (refresh_tokens), where they are revocable.</li>
 *   <li><b>CSRF disabled globally, enforced where it matters:</b> bearer-token
 *       requests cannot be forged cross-site (the attacker cannot read the token),
 *       while the two cookie-authenticated endpoints ({@code /auth/refresh},
 *       {@code /auth/logout}) are protected by a double-submit CSRF token inside
 *       {@link com.YemomBizGrow.auth.AuthService}.</li>
 *   <li><b>CORS with an exact allowlist:</b> credentials (cookies) are enabled, so
 *       wildcard origins are rejected by StartupValidator.</li>
 * </ul></p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppProperties properties;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityErrorResponder errorResponder;

    public SecurityConfig(AppProperties properties,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          SecurityErrorResponder errorResponder) {
        this.properties = properties;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.errorResponder = errorResponder;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Credential issuance and recovery endpoints
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/verify-email"
                        ).permitAll()
                        // Public endpoints (lead capture forms, chatbot widgets)
                        .requestMatchers("/api/v1/public/**").permitAll()
                        // Infrastructure: health probes and API documentation
                        .requestMatchers(
                                "/actuator/health", "/actuator/health/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/swagger-ui/index.html"
                        ).permitAll()
                        // Everything else requires a valid access token;
                        // organization permissions are enforced inside services.
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((request, response, authException) ->
                                errorResponder.write(response, HttpStatus.UNAUTHORIZED,
                                        "UNAUTHORIZED", "Authentication required."))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                errorResponder.write(response, HttpStatus.FORBIDDEN,
                                        "FORBIDDEN", "You do not have permission to perform this action.")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.getSecurity().getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
                "X-CSRF-TOKEN", "X-Requested-With"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
