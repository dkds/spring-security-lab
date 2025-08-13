package com.example.service;

import com.example.dto.UserAuthProviderRequest;
import com.example.dto.UserDto;
import com.example.dto.UserLoginRequest;
import com.example.dto.UserRegisterRequest;
import com.example.entity.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

public interface UserService extends UserDetailsService {
    static UserDto mapToDto(User entity) {
        return new UserDto(
                entity.getEmail(),
                entity.getRole().name(),
                entity.getAuthProviders().stream().map(AuthProviderService::mapToDto).toList());
    }

    UserDto registerAuthProvider(UserAuthProviderRequest request);

    UserDto registerUser(UserRegisterRequest request);

    UserDto login(UserLoginRequest request);

    List<UserDto> listUsers();
}
