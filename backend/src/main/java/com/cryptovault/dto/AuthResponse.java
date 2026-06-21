package com.cryptovault.dto;

/** Response returned by register and login: the freshly minted JWT. */
public record AuthResponse(String token) {
}
