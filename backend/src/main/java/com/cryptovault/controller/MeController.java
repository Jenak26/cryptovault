package com.cryptovault.controller;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A minimal protected endpoint that echoes the authenticated caller. It exists so the JWT filter and
 * the logout blacklist can be exercised end-to-end: a valid token returns 200, and a logged-out
 * (blacklisted) token returns 401.
 */
@RestController
@RequestMapping("/api")
public class MeController {

    @GetMapping("/me")
    public Map<String, String> me(Authentication authentication) {
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        return Map.of(
                "userId", authentication.getName(),
                "role", role
        );
    }
}
