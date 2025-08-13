package com.example.exceptions;

public class AuthProviderNotFoundException extends IllegalArgumentException {
    public AuthProviderNotFoundException(String providerName) {
        super("Auth provider with name " + providerName + " not found");
    }
}
