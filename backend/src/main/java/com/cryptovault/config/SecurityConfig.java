package com.cryptovault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Phase 0 security baseline.
 *
 * For now this only opens up the public endpoints (health + auth) and locks down
 * everything else. The real JWT filter and role rules arrive in Phase 3.
 *
 * Notes:
 *  - CSRF is disabled because this is a stateless JSON API (no browser form posts /
 *    session cookies). Protection comes from the JWT, added later.
 *  - SessionCreationPolicy.STATELESS: the server keeps no session; each request must
 *    carry its own proof of identity (a JWT). This is standard for REST APIs.
 *  - httpBasic was removed (it was a Phase 0 placeholder); auth is now via JWT.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/actuator/health", "/api/auth/**").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }

    /** Password hashing for register/login. BCrypt with the default strength (10). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
