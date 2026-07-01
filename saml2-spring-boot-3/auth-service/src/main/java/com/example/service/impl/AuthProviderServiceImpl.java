package com.example.service.impl;

import com.example.config.ConfigProperties;
import com.example.dto.AuthProviderDto;
import com.example.dto.SSOInitiateRequestDto;
import com.example.dto.SSOType;
import com.example.dto.UserAuthProviderListRequest;
import com.example.entity.AuthProvider;
import com.example.entity.User;
import com.example.exceptions.UserNotFoundException;
import com.example.repository.AuthProviderRepository;
import com.example.repository.UserRepository;
import com.example.service.AuthProviderService;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@RequiredArgsConstructor
@Service
public class AuthProviderServiceImpl implements AuthProviderService {

    private final ConfigProperties properties;
    private final AuthProviderRepository authProviderRepository;
    private final UserRepository userRepository;
    private final ApplicationContext applicationContext;

    @PostConstruct
    @Transactional
    public void init() {
        // Initialize default asserting party

        var defaultAuthProvider = new AuthProviderDto(
                properties.saml().defaultAssertingParty().name(),
                SSOType.SAML,
                properties.saml().defaultAssertingParty().metadataLocation(),
                "");
        saveAuthProvider(defaultAuthProvider);
//        var provider = authProviderRepository.findByName(properties.saml().defaultAssertingParty().name());
//        if (provider.isEmpty()) {
//            var defaultRelyingParty = new AuthProvider();
//            defaultRelyingParty.setName(properties.saml().defaultAssertingParty().name());
//            defaultRelyingParty.setMetadataLocation(properties.saml().defaultAssertingParty().metadataLocation());
//            authProviderRepository.save(defaultRelyingParty);
//        }
    }

    @Override
    public List<AuthProviderDto> listByUser(UserAuthProviderListRequest request) {
        return userRepository.findByEmail(request.email())
                .map(User::getAuthProviders)
                .map(providers -> providers.stream().map(AuthProviderService::mapToDto).toList())
                .orElseThrow(() -> new UserNotFoundException(request.email()));
    }

    @Transactional
    @Override
    public AuthProviderDto saveAuthProvider(AuthProviderDto request) {
        var authProvider = authProviderRepository.findByName(request.name()).orElse(new AuthProvider());
        authProvider.setName(request.name());
        authProvider.setMetadataLocation(request.metadataLocation());

        var rpRegistration = RelyingPartyRegistrations
                .fromMetadataLocation(request.metadataLocation())
                .registrationId(request.name())
                .build();
        var apMetadata = rpRegistration.getAssertingPartyMetadata();
        authProvider.setSsoServiceLocation(apMetadata.getSingleSignOnServiceLocation());

        var savedAuthProvider = authProviderRepository.save(authProvider);

        var registry = (DefaultSingletonBeanRegistry) applicationContext.getAutowireCapableBeanFactory();
        registry.destroySingleton("relyingPartyRegistrations");

        return AuthProviderService.mapToDto(savedAuthProvider);
    }

    @Override
    public List<AuthProviderDto> listAuthProviders() {
        return authProviderRepository.findAll()
                .stream()
                .map(AuthProviderService::mapToDto)
                .toList();
    }

    @Override
    public AuthProviderDto getProviderDetailsForSSO(SSOInitiateRequestDto request) {
        return userRepository.findByEmail(request.username())
                .map(User::getAuthProviders)
                .stream()
                .flatMap(Collection::stream)
                .filter(provider -> provider.getName().equals(request.providerName()))
                .findFirst()
                .map(AuthProviderService::mapToDto)
                .orElseThrow(() -> new IllegalArgumentException("providerName not assigned to user"));
    }
}
