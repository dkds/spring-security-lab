package com.dkds.commonsecurity;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/// Minimal PEM parsing for RSA keys — PKCS8 private keys
/// ("-----BEGIN PRIVATE KEY-----") and X.509 SubjectPublicKeyInfo public keys
/// ("-----BEGIN PUBLIC KEY-----"). Deliberately dependency-free: only the
/// java.security APIs every JVM already has, no PEM/crypto library needed for
/// something this small.
public final class PemUtils {

    private PemUtils() {
    }

    public static RSAPrivateKey readRSAPrivateKey(Resource resource) {
        try {
            var keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decode(resource)));
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Failed to read RSA private key from " + resource, ex);
        }
    }

    public static RSAPublicKey readRSAPublicKey(Resource resource) {
        try {
            var keyFactory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(decode(resource)));
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Failed to read RSA public key from " + resource, ex);
        }
    }

    private static byte[] decode(Resource resource) throws IOException {
        String pem;
        try (InputStream in = resource.getInputStream()) {
            pem = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String base64 = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
