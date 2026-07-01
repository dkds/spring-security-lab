package com.example.dto;

public record UserRegisterRequest(String email, String password, String confirmPassword) {
}
