package com.example.dto;

import java.util.List;

public record UserDto(String email, String role, List<AuthProviderDto> providers) {
}
