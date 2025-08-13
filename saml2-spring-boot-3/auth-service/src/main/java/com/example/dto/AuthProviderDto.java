package com.example.dto;

public record AuthProviderDto(String name, SSOType type, String metadataLocation, String redirectLocation) {
}
