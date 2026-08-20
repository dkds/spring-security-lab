package com.dkds.authcodetest2.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HelloController {

    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello(JwtAuthenticationToken token) {
        return ResponseEntity.ok(Map.of(
                "message", "Hello, " + token.getName(),
                "scopes", token.getAuthorities().toString()
        ));
    }
}