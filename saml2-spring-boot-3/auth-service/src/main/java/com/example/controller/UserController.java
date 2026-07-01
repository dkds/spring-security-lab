package com.example.controller;

import com.example.dto.AuthProviderDto;
import com.example.dto.UserAuthProviderListRequest;
import com.example.dto.UserAuthProviderRequest;
import com.example.dto.UserDto;
import com.example.dto.UserLoginRequest;
import com.example.dto.UserRegisterRequest;
import com.example.service.AuthProviderService;
import com.example.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/users")
public class UserController {

    private final UserService userService;
    private final AuthProviderService authProviderService;

    @PostMapping("register")
    public ResponseEntity<UserDto> register(@RequestBody UserRegisterRequest request) {
        return ResponseEntity.ok(userService.registerUser(request));
    }

    @PostMapping("login")
    public ResponseEntity<UserDto> login(@RequestBody UserLoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @PostMapping("auth-providers")
    public ResponseEntity<List<AuthProviderDto>> getAuthProviderDetails(@RequestBody UserAuthProviderListRequest request) {
        return ResponseEntity.ok(authProviderService.listByUser(request));
    }

    @PutMapping("auth-providers")
    public ResponseEntity<UserDto> registerAuthProvider(@RequestBody UserAuthProviderRequest request) {
        return ResponseEntity.ok(userService.registerAuthProvider(request));
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> listUsers() {
        return ResponseEntity.ok(userService.listUsers());
    }
}
