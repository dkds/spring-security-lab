package com.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record ConfigProperties(Saml saml, Auth auth, Crypto crypto) {

    public record Saml(DefaultAssertingParty defaultAssertingParty, RelyingParty relyingParty) {
    }

    public record DefaultAssertingParty(String name, String metadataLocation) {
    }

    public record RelyingParty(String signingCertLocation, String signingKeyLocation) {
    }

    public record AdminUser(String email, String password) {
    }

    public record Auth(AdminUser adminUser, String kfName, String ksAlias, String ksPass) {
    }

    public record Crypto(String password, String salt) {
    }
}

