package com.cryptovault.config;

import com.cryptovault.security.JwtAccessDeniedHandler;
import com.cryptovault.security.JwtAuthEntryPoint;
import com.cryptovault.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
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

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration.
 *
 * <ul>
 *   <li>CSRF disabled: stateless JSON API (no browser form posts / session cookies); the JWT is the
 *       proof of identity.</li>
 *   <li>STATELESS sessions: the server keeps no session; every request carries its own token.</li>
 *   <li>{@link JwtAuthFilter} runs before the username/password filter, validates the bearer token,
 *       checks the Redis blacklist, and authenticates the request.</li>
 *   <li>{@link JwtAuthEntryPoint} returns a clean 401 (not 403) on unauthenticated access.</li>
 * </ul>
 *
 * <p>Method-level RBAC ({@code @PreAuthorize}) and login rate-limiting are enforced elsewhere;
 * CORS origins are configurable via {@code cryptovault.cors.allowed-origins} so the deployed
 * frontend (a different origin) can call the API.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

    /** Comma-separated list of allowed browser origins. Dev default is the Vite dev server. */
    @Value("${cryptovault.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          JwtAuthEntryPoint jwtAuthEntryPoint,
                          JwtAccessDeniedHandler jwtAccessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.jwtAuthEntryPoint = jwtAuthEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: liveness, auth endpoints, and the dev-only test console.
                        .requestMatchers("/api/health", "/actuator/health", "/api/auth/**",
                                "/dev-console.html", "/error",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(jwtAuthEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Password hashing for register/login. BCrypt with the default strength (10). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** CORS for the browser SPA. Origins come from config so prod can lock this to the real domain. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
