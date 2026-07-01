package com.example.dto;

public enum UserAuthority {
    ADMIN, USERS_LIST, PROVIDERS_LIST;

    public String scope() {
        return "SCOPE_" + name();
    }
}
