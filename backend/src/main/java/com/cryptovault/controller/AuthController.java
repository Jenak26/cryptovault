package com.cryptovault.controller;

import com.cryptovault.dto.AuthResponse;
import com.cryptovault.dto.LoginRequest;
import com.cryptovault.dto.LoginResponse;
import com.cryptovault.dto.MfaVerifyRequest;
import com.cryptovault.dto.RegisterRequest;
import com.cryptovault.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints. Login may return either a JWT or an MFA challenge; the second
 * factor is completed at {@code /api/auth/mfa/verify}. Identity for logout comes from the bearer
 * token itself.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return auth.login(request, getClientIp(servletRequest));
    }

    @PostMapping("/mfa/verify")
    public AuthResponse verifyMfa(@Valid @RequestBody MfaVerifyRequest request, HttpServletRequest servletRequest) {
        return auth.verifyMfa(request.mfaToken(), request.code(), getClientIp(servletRequest));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    public void logout(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.startsWith(BEARER_PREFIX)
                ? authorizationHeader.substring(BEARER_PREFIX.length())
                : authorizationHeader;
        auth.logout(token);
    }

    private String getClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isEmpty()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
