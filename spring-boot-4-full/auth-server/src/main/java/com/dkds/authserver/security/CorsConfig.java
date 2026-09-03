package com.dkds.authserver.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/// CORS configuration for OAuth2 endpoints.
/// Allows the UI (localhost:5173) to call token endpoint and discovery endpoints.
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        
        // Allow requests from UI
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:5173",
            "http://localhost:5174",
            "http://127.0.0.1:5173"
        ));
        
        // Allow methods needed for OAuth2 flow
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "OPTIONS"));
        
        // Allow headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Content-Type",
            "Authorization"
        ));
        
        // Allow credentials (cookies/session)
        configuration.setAllowCredentials(true);
        
        // Cache preflight response
        configuration.setMaxAge(3600L);
        
        // Apply to all authorization server endpoints the SPA needs to call
        // directly (token exchange, userinfo, discovery, JWK Set).
        // Note: /oauth2/authorize is browser-navigated (not XHR), so no CORS needed there.
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/oauth2/token", configuration);
        source.registerCorsConfiguration("/oauth2/jwks", configuration);
        source.registerCorsConfiguration("/oauth2/introspect", configuration);
        source.registerCorsConfiguration("/oauth2/revoke", configuration);
        source.registerCorsConfiguration("/userinfo", configuration);
        source.registerCorsConfiguration("/connect/logout", configuration);
        source.registerCorsConfiguration("/.well-known/**", configuration);

        return source;
    }
}
