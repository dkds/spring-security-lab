package com.example.dto;

import java.time.Instant;

public record UserAuthCode(String username, long expiresAt) {
    public void validateOrThrow() {
        var now = Instant.now();
        if (now.isAfter(Instant.ofEpochSecond(expiresAt))) {
            throw new IllegalArgumentException("User auth code expired");
        }
    }
}
