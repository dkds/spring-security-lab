package com.dkds.test.auth_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
@Data
public class ConfigProperties {

    private Jwk jwk;

    @Data
    public static class Jwk {
        private String keystorePath;
        private String keystoreType;
        private String keystorePassword;
        private String keyAlias;
        private String keyPassword;
    }
}
