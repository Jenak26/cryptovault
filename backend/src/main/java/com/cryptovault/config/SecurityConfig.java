package com.cryptovault.config;

import com.cryptovault.security.JwtAuthEntryPoint;
import com.cryptovault.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
 * <p>Role-based authorization rules (RBAC) and login rate-limiting arrive in Phase 3.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthEntryPoint jwtAuthEntryPoint;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, JwtAuthEntryPoint jwtAuthEntryPoint) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.jwtAuthEntryPoint = jwtAuthEntryPoint;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public: liveness, auth endpoints, and the dev-only test console.
                        .requestMatchers("/api/health", "/actuator/health", "/api/auth/**",
                                "/dev-console.html").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Password hashing for register/login. BCrypt with the default strength (10). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
