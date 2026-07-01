package com.example.util;

import com.example.dto.EntityDescriptor;
import com.example.dto.UserToken;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class Util {


    public static final String AUTH_REQUEST_PARAM_USERNAME = "username";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Base64.Encoder TOKEN_ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder TOKEN_DECODER = Base64.getDecoder();

    public static String generateToken(String userEmail, String role) {
        var uuid = UUID.randomUUID();
        var userToken = new UserToken(uuid.toString(), userEmail, role);
        try {
            var jsonToken = OBJECT_MAPPER.writeValueAsString(userToken);
            return TOKEN_ENCODER.encodeToString(jsonToken.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static UserToken extractToken(String token) {
        var decoded = TOKEN_DECODER.decode(token);
        var jsonToken = new String(decoded, StandardCharsets.UTF_8);
        try {
            return OBJECT_MAPPER.readValue(jsonToken, UserToken.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String extractSamlMetadata(String metadataLocation) {
        var metadata = RestClient.create(metadataLocation).get().retrieve().body(EntityDescriptor.class);
        if (metadata != null
                && metadata.getIdpSSODescriptor() != null
                && metadata.getIdpSSODescriptor().getSingleSignOnService() != null) {
            return metadata.getIdpSSODescriptor().getSingleSignOnService().getFirst().getLocation();
        }
        return null;
    }
}
