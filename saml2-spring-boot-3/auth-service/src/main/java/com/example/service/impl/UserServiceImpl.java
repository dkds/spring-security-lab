package com.example.service.impl;

import com.example.config.ConfigProperties;
import com.example.dto.UserAuthProviderRequest;
import com.example.dto.UserDto;
import com.example.dto.UserLoginRequest;
import com.example.dto.UserRegisterRequest;
import com.example.dto.UserRole;
import com.example.entity.User;
import com.example.exceptions.AuthProviderNotFoundException;
import com.example.exceptions.UserAlreadyExistsException;
import com.example.exceptions.UserAuthenticationException;
import com.example.exceptions.UserNotFoundException;
import com.example.repository.AuthProviderRepository;
import com.example.repository.UserRepository;
import com.example.service.UserService;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    private final ConfigProperties properties;
    private final UserRepository userRepository;
    private final AuthProviderRepository authProviderRepository;
    private final PasswordEncoder passwordEncoder;

    @PostConstruct
    @Transactional
    void init() {
        // Initialize default admin user
        var userOptional = userRepository.findByEmail(properties.auth().adminUser().email());
        if (userOptional.isEmpty()) {
            var adminUser = new User();
            adminUser.setEmail(properties.auth().adminUser().email());
            adminUser.setPassword(passwordEncoder.encode(properties.auth().adminUser().password()));
            adminUser.setRole(UserRole.ADMIN);
            adminUser.setAuthProviders(Collections.emptyList());
            userRepository.save(adminUser);
        }
    }

    @Transactional
    @Override
    public UserDto registerAuthProvider(UserAuthProviderRequest request) {
        var user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserNotFoundException(request.email()));
        var provider = authProviderRepository.findByName(request.providerName())
                .orElseThrow(() -> new AuthProviderNotFoundException(request.providerName()));
        user.getAuthProviders().add(provider);
        var savedUser = userRepository.save(user);

        return UserService.mapToDto(savedUser);
    }

    @Transactional
    @Override
    public UserDto registerUser(UserRegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        userRepository.findByEmail(request.email()).ifPresent((user) -> {
            throw new UserAlreadyExistsException(user.getEmail());
        });
        var user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setAuthProviders(Collections.emptyList());
        user.setRole(UserRole.USER);
        var savedUser = userRepository.save(user);
        return UserService.mapToDto(savedUser);
    }

    @Override
    public UserDto login(UserLoginRequest request) {
        var user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UserAuthenticationException(request.email()));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UserAuthenticationException(request.email());
        }
        return UserService.mapToDto(user);
    }

    @Override
    public List<UserDto> listUsers() {
        return userRepository.findAll().stream()
                .map(UserService::mapToDto)
                .toList();
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("Username not found: " + username));
    }
}
