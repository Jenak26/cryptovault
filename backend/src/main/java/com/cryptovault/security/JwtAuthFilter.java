package com.cryptovault.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the bearer token on every request and, if good, authenticates the caller.
 *
 * <p>A token is rejected (left unauthenticated) when it is missing, malformed, expired, tampered, or
 * its {@code jti} has been blacklisted by logout. The authority is {@code ROLE_<role>} so Phase 3's
 * RBAC rules can build on it.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwt;
    private final TokenBlacklist blacklist;

    public JwtAuthFilter(JwtService jwt, TokenBlacklist blacklist) {
        this.jwt = jwt;
        this.blacklist = blacklist;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Claims claims = jwt.parseClaims(token);
                if (!blacklist.isBlacklisted(claims.getId())) {
                    String userId = claims.getSubject();
                    String role = claims.get("role", String.class);
                    var authority = new SimpleGrantedAuthority("ROLE_" + role);
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, List.of(authority));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException ex) {
                // Malformed, expired, or tampered token -> stay unauthenticated; the entry point 401s.
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
