package com.cryptovault.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cryptovault.dto.AuthResponse;
import com.cryptovault.dto.LoginRequest;
import com.cryptovault.dto.RegisterRequest;
import com.cryptovault.entity.Role;
import com.cryptovault.entity.User;
import com.cryptovault.exception.EmailAlreadyExistsException;
import com.cryptovault.exception.InvalidCredentialsException;
import com.cryptovault.repository.RoleRepository;
import com.cryptovault.repository.UserRepository;
import com.cryptovault.security.JwtService;
import com.cryptovault.security.RateLimiter;
import com.cryptovault.security.TokenBlacklist;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository users;
    @Mock
    private RoleRepository roles;
    @Mock
    private PasswordEncoder encoder;
    @Mock
    private JwtService jwt;
    @Mock
    private TokenBlacklist blacklist;
    @Mock
    private RateLimiter rateLimiter;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private AuthService auth;

    private Role userRole() {
        Role role = new Role();
        role.setId(2L);
        role.setName("USER");
        return role;
    }

    @Test
    void registerHashesPasswordAndReturnsToken() {
        when(users.existsByEmail("a@b.com")).thenReturn(false);
        when(roles.findByName("USER")).thenReturn(Optional.of(userRole()));
        when(encoder.encode("password123")).thenReturn("HASHED");
        when(jwt.generateToken(any(UUID.class), eq("USER"))).thenReturn("token-123");

        AuthResponse response = auth.register(new RegisterRequest("a@b.com", "password123"));

        assertThat(response.token()).isEqualTo("token-123");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("a@b.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("HASHED");
        assertThat(saved.getValue().getRole().getName()).isEqualTo("USER");
        assertThat(saved.getValue().getId()).isNotNull();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmail("a@b.com")).thenReturn(true);

        assertThatThrownBy(() -> auth.register(new RegisterRequest("a@b.com", "password123")))
                .isInstanceOf(EmailAlreadyExistsException.class);
        verify(users, never()).save(any());
    }

    @Test
    void loginReturnsTokenForCorrectPassword() {
        User user = User.builder()
                .id(UUID.randomUUID()).email("a@b.com").passwordHash("HASHED").role(userRole()).build();
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(encoder.matches("password123", "HASHED")).thenReturn(true);
        when(jwt.generateToken(user.getId(), "USER")).thenReturn("token-123");

        AuthResponse response = auth.login(new LoginRequest("a@b.com", "password123"));

        assertThat(response.token()).isEqualTo("token-123");
    }

    @Test
    void loginRejectsUnknownEmailGenerically() {
        when(users.findByEmail("missing@b.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> auth.login(new LoginRequest("missing@b.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsWrongPasswordGenerically() {
        User user = User.builder()
                .id(UUID.randomUUID()).email("a@b.com").passwordHash("HASHED").role(userRole()).build();
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(encoder.matches("wrongpass", "HASHED")).thenReturn(false);

        assertThatThrownBy(() -> auth.login(new LoginRequest("a@b.com", "wrongpass")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void logoutBlacklistsJtiWithRemainingTtl() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getExpiration()).thenReturn(Date.from(Instant.now().plusSeconds(1800)));
        when(jwt.parseClaims("token-123")).thenReturn(claims);

        auth.logout("token-123");

        verify(blacklist).blacklist(eq("jti-1"), any(Duration.class));
    }
}
