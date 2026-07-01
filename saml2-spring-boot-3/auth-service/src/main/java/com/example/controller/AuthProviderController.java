package com.example.controller;

import com.example.dto.AuthProviderDto;
import com.example.service.AuthProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("api/auth-providers")
public class AuthProviderController {

    private final AuthProviderService authProviderService;

    @PostMapping
    public ResponseEntity<AuthProviderDto> registerAuthProvider(@RequestBody AuthProviderDto request) {
        return ResponseEntity.ok(authProviderService.saveAuthProvider(request));
    }

    @GetMapping
    public ResponseEntity<List<AuthProviderDto>> listAuthProviders() {
        return ResponseEntity.ok(authProviderService.listAuthProviders());
    }

}
