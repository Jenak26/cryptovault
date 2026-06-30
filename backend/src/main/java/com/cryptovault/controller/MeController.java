package com.cryptovault.controller;

import com.cryptovault.entity.User;
import com.cryptovault.repository.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A minimal protected endpoint that echoes the authenticated caller. It exists so the JWT filter and
 * the logout blacklist can be exercised end-to-end: a valid token returns 200, and a logged-out
 * (blacklisted) token returns 401. Also surfaces whether MFA is enabled so the UI can reflect it.
 */
@RestController
@RequestMapping("/api")
public class MeController {

    private final UserRepository users;

    public MeController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        boolean mfaEnabled = users.findById(UUID.fromString(authentication.getName()))
                .map(User::isMfaEnabled)
                .orElse(false);
        return Map.of(
                "userId", authentication.getName(),
                "role", role,
                "mfaEnabled", mfaEnabled
        );
    }
}
