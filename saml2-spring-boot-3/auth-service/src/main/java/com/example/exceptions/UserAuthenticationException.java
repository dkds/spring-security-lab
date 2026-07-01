package com.example.exceptions;

public class UserAuthenticationException extends IllegalArgumentException {
    public UserAuthenticationException(String email) {
        super("User authentication failed for user: " + email);
    }
}
