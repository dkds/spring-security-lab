package com.example.exceptions;

public class UserAlreadyExistsException extends IllegalArgumentException {
    public UserAlreadyExistsException(String email) {
        super("User with email '" + email + "' already exists.");
    }
}
