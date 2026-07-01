package com.example.exceptions;

public class UserNotFoundException extends IllegalArgumentException {
    public UserNotFoundException(String email) {
        super("User with email " + email + " not found");
    }
}
