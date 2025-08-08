package com.example.config;

import com.example.dto.UserAuthCode;
import com.example.dto.UserAuthority;
import com.example.entity.User;
import com.example.repository.AuthProviderRepository;
import com.example.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.opensaml.security.x509.X509Support;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.DelegatingAuthenticationConverter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.security.config.Customizer.withDefaults;

@RequiredArgsConstructor
@EnableWebSecurity
@Configuration
public class AuthConfig {

    private static final String ALL_URLS_PATTERN = "/**";
    private final ConfigProperties properties;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Map<String, UserAuthCode> userAuthCodeMap() {
        return new HashMap<>();
    }

    @Bean
    public TextEncryptor textEncryptor() {
        return Encryptors.text(properties.crypto().password(), properties.crypto().salt());
    }

    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(UserService userService, PasswordEncoder passwordEncoder) {
        var daoAuthenticationProvider = new DaoAuthenticationProvider(userService);
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder);
        return daoAuthenticationProvider;
    }

    @SuppressWarnings("removal")
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        var clientRegistration = RegisteredClient.withId("default-client")
                .clientId("oauth2-client")
                .clientSecret("$2a$10$C2CREpYoNldLhYGy6eGrCuxhxhASmHZF7PU.DCzh3KDu0jiY7tKr6")
                .authorizationGrantType(AuthorizationGrantType.PASSWORD)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder()
                        .reuseRefreshTokens(false)
                        .accessTokenTimeToLive(Duration.of(2, ChronoUnit.MINUTES))
                        .refreshTokenTimeToLive(Duration.of(30, ChronoUnit.DAYS)).build())
                .build();
        return new InMemoryRegisteredClientRepository(clientRegistration);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Collections.singletonList("*"));
        configuration.setAllowedMethods(Arrays.asList(
                HttpMethod.HEAD.name(),
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PUT.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.PATCH.name()));
        configuration.setAllowCredentials(true);
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.addExposedHeader(HttpHeaders.AUTHORIZATION);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return (context) -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                if (context.getPrincipal() instanceof UsernamePasswordAuthenticationToken token) {
                    var user = (User) token.getPrincipal();
                    context.getClaims().claim(
                            OAuth2ParameterNames.SCOPE,
                            user.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList()
                    );
                }
            }
        };
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() throws Exception {
        var keystorePath = properties.auth().kfName();
        var keystorePassword = properties.auth().ksPass();
        var keyAlias = properties.auth().ksAlias();

        var keyStore = KeyStore.getInstance("JKS");

        try (var is = new ClassPathResource(keystorePath).getInputStream()) {
            keyStore.load(is, keystorePassword.toCharArray());
        }

        var privateKey = (RSAPrivateKey) keyStore.getKey(keyAlias, keystorePassword.toCharArray());
        var publicKey = (RSAPublicKey) keyStore.getCertificate(keyAlias).getPublicKey();

        var rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("mbtp-key-id")
                .build();

        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public OAuth2TokenGenerator<OAuth2Token> tokenGenerator(JwtEncoder jwtEncoder, OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer) {
        var jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(tokenCustomizer);
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator
        );
    }

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrations(AuthProviderRepository authProviderRepository) throws Exception {
        var signingCertResource = new ClassPathResource(properties.saml().relyingParty().signingCertLocation());
        var signingKeyResource = new ClassPathResource(properties.saml().relyingParty().signingKeyLocation());
        try (
                var rpSigningCert = signingCertResource.getInputStream();
                var rpSigningKey = signingKeyResource.getInputStream()
        ) {
            var rpCertificate = X509Support.decodeCertificate(rpSigningCert.readAllBytes());
            var rpKey = RsaKeyConverters.pkcs8().convert(rpSigningKey);
            assert rpCertificate != null;
            assert rpKey != null;
            var rpSigningCredentials = Saml2X509Credential.signing(rpKey, rpCertificate);

            var authProviders = authProviderRepository.findAll();
            var authRegistrations = authProviders.stream()
                    .map(provider ->
                            RelyingPartyRegistrations
                                    .fromMetadataLocation(provider.getMetadataLocation())
                                    .registrationId(provider.getName())
                                    .signingX509Credentials(c -> c.add(rpSigningCredentials))
                                    .build())
                    .toList();
            return new InMemoryRelyingPartyRegistrationRepository(authRegistrations);
        }
    }

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity,
                                                   PasswordAuthenticationConverter passwordAuthenticationConverter,
                                                   UserAuthCodeAuthenticationConverter userAuthCodeAuthenticationConverter,
                                                   DaoAuthenticationProvider daoAuthenticationProvider,
                                                   UserService userService,
                                                   OAuth2TokenGenerator<OAuth2Token> tokenGenerator,
                                                   ObjectMapper objectMapper,
                                                   TextEncryptor textEncryptor
    ) throws Exception {

        var authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer.authorizationServer();

        httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults()) // CORS configuration is handled by the CorsFilter at portal-platform-framework
                .with(authorizationServerConfigurer, (authorizationServer) -> authorizationServer
                        .tokenEndpoint((tokenEndpoint) -> tokenEndpoint
                                .accessTokenRequestConverter(new DelegatingAuthenticationConverter(
                                        passwordAuthenticationConverter,
                                        userAuthCodeAuthenticationConverter
                                ))
                        )
                )
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests.anyRequest().authenticated()
                )
                .saml2Login(withDefaults())
                .saml2Logout(withDefaults())
                .saml2Metadata(withDefaults())
                .authenticationProvider(new PasswordAuthenticationProvider(daoAuthenticationProvider, tokenGenerator))
                .authenticationProvider(new UserAuthCodeAuthenticationProvider(tokenGenerator, userService, objectMapper, textEncryptor))
                .securityMatcher(new OrRequestMatcher(
                        authorizationServerConfigurer.getEndpointsMatcher(),
                        PathPatternRequestMatcher.withDefaults().matcher("/saml2/**"),
                        PathPatternRequestMatcher.withDefaults().matcher("/login/saml2/**")
                ));
        return httpSecurity.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity httpSecurity,
                                                                 JwtDecoder jwtDecoder) throws Exception {
        httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults()) // CORS configuration is handled by the CorsFilter at portal-platform-framework
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/users").hasAnyAuthority(UserAuthority.ADMIN.scope(), UserAuthority.USERS_LIST.scope())
                        .requestMatchers(HttpMethod.PUT, "/api/users/auth-providers").hasAnyAuthority(UserAuthority.ADMIN.scope())
                        .requestMatchers(HttpMethod.GET, "/api/auth-providers").hasAnyAuthority(UserAuthority.ADMIN.scope(), UserAuthority.PROVIDERS_LIST.scope())
                        .requestMatchers(HttpMethod.POST, "/api/auth-providers").hasAnyAuthority(UserAuthority.ADMIN.scope())

                        .requestMatchers(HttpMethod.POST, "/api/users/auth-providers").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/users/login").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .requestMatchers("/sso/**").permitAll()
                        .requestMatchers("/login/**").permitAll()
                        .requestMatchers(ALL_URLS_PATTERN).denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                );
        return httpSecurity.build();
    }
}
