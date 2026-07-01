package com.example.service;

import com.example.dto.AuthProviderDto;
import com.example.dto.SSOInitiateRequestDto;
import com.example.dto.SSOType;
import com.example.dto.UserAuthProviderListRequest;
import com.example.entity.AuthProvider;

import java.util.List;

public interface AuthProviderService {
    static AuthProviderDto mapToDto(AuthProvider entity) {
        return new AuthProviderDto(entity.getName(), SSOType.SAML, entity.getMetadataLocation(), entity.getSsoServiceLocation());
    }

    List<AuthProviderDto> listByUser(UserAuthProviderListRequest request);

    AuthProviderDto saveAuthProvider(AuthProviderDto request);

    List<AuthProviderDto> listAuthProviders();

    AuthProviderDto getProviderDetailsForSSO(SSOInitiateRequestDto request);
}
