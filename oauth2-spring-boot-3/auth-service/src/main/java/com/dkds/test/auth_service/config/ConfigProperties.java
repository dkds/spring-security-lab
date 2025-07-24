package com.dkds.test.auth_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Data
public class ConfigProperties {

    private Jwk jwk;
    private Auth auth;

    @Data
    public static class Jwk {
        private String keystorePath;
        private String keystoreType;
        private String keystorePassword;
        private String keyAlias;
        private String keyPassword;
    }

    @Data
    public static class Auth {
        private String username;
        private String password;
        private String clientId;
        private String clientSecret;
        private String callbackUri;
        private String postLogoutCallbackUri;
    }
}
