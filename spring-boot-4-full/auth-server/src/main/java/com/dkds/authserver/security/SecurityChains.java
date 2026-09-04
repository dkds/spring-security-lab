package com.dkds.authserver.security;

import com.dkds.authserver.login.FormLoginConfigurer;
import com.dkds.authserver.onetimetoken.EmailOttDeliveryHandler;
import com.dkds.authserver.onetimetoken.OneTimeTokenConfigurer;
import com.dkds.authserver.sso.Saml2Configurer;
import com.dkds.authserver.sso.SsoDiscoveryController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.annotation.authorization.EnableMultiFactorAuthentication;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.ott.GenerateOneTimeTokenFilter;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;

/// Defines exactly three filter chains, in the order mandated by DESIGN.md.
///
/// | Order | Matcher                             | Contents                                             |
/// |-------|-------------------------------------|------------------------------------------------------|
/// | 1     | authServer.getEndpointsMatcher()    | Authorization server + OIDC; anyRequest authenticated|
/// | 2     | /api/**                             | JWT resource server, STATELESS, CSRF off, CORS on    |
/// | 3     | (fallback)                          | form login, OTT, SAML2, captcha, session strategy    |
@Configuration
@RequiredArgsConstructor
@Slf4j
@EnableMultiFactorAuthentication(authorities = {})
public class SecurityChains {

    private final FormLoginConfigurer formLoginConfigurer;
    private final OneTimeTokenConfigurer oneTimeTokenConfigurer;
    private final Saml2Configurer saml2Configurer;

    /// Chain 1: Authorization server + OIDC endpoints.
    ///
    /// Uses the authorization server configurer's own endpoint matcher, which
    /// already includes the OIDC discovery endpoints (/.well-known/**). OIDC is
    /// enabled explicitly. anyRequest().authenticated() is correct here — the
    /// global AuthorizationManagerFactory composes the factor requirement into it.
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Chain 1: Authorization Server + OIDC");

        // Apply the configurer first so getEndpointsMatcher() returns the full
        // set of authorization server endpoints including /oauth2/authorize.
        var authServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        http.with(authServerConfigurer, server -> server.oidc(Customizer.withDefaults()));

        // Now the matcher is fully populated.
        var endpointsMatcher = authServerConfigurer.getEndpointsMatcher();

        http
                .securityMatcher(endpointsMatcher)
                .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher))
                .exceptionHandling(ex -> ex
                        // Unauthenticated browser requests to /oauth2/authorize
                        // must be redirected to the login page (Chain 3), not
                        // rejected with 401.
                        .authenticationEntryPoint(
                                new LoginUrlAuthenticationEntryPoint(SecurityConstants.LOGIN_PAGE))
                        // OneTimeTokenConfigurer registers this same
                        // missing-FACTOR_OTT routing automatically, but only
                        // on the HttpSecurity it's applied to (Chain 3). This
                        // chain never carries that configurer — form login,
                        // OTT and SAML2 all live in Chain 3 only, per
                        // DESIGN.md — yet /oauth2/authorize (this chain) is
                        // exactly where the composed AuthorizationManagerFactory
                        // first denies a principal missing FACTOR_OTT. Without
                        // this, that denial falls through to the default
                        // AccessDeniedHandler and returns a bare 403 instead of
                        // routing the browser to the OTT flow. Points at
                        // OTT_REQUEST_URL, not LOGIN_PAGE — see
                        // OneTimeTokenConfigurer for why.
                        .defaultDeniedHandlerForMissingAuthority(
                                ep -> ep.addEntryPointFor(
                                        new LoginUrlAuthenticationEntryPoint(EmailOttDeliveryHandler.OTT_REQUEST_URL),
                                        new NegatedRequestMatcher(FormLoginConfigurer.BEARER_TOKEN_MATCHER)),
                                FactorGrantedAuthority.OTT_AUTHORITY));

        return http.build();
    }

    /// Chain 2: JWT resource server for /api/**. STATELESS, CSRF off, CORS on.
    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        log.info("Configuring Chain 2: API Resource Server (STATELESS)");

        http
                .securityMatcher(SecurityConstants.API_MATCHER)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    /// Chain 3 (fallback): form login (+ OTT, SAML2, captcha in later phases).
    ///
    /// Per DESIGN.md, form login / OTT / SAML2 all live in ONE chain. Each
    /// mechanism ships an AbstractHttpConfigurer applied here via http.with(...).
    @Bean
    @Order(3)
    public SecurityFilterChain fallbackSecurityFilterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Chain 3: Fallback (form login)");

        http
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(SecurityConstants.LOGIN_PAGE).permitAll()
                        // The org-discovery/picker flow (enter username, list
                        // the SSO-eligible orgs, pick one) — necessarily
                        // reachable pre-authentication, since it's how a user
                        // gets to an IdP in the first place. See
                        // SsoDiscoveryController's own javadoc on why it must
                        // never let this endpoint distinguish an unknown
                        // username from a known one with no SSO org.
                        .requestMatchers(SsoDiscoveryController.SSO_DISCOVER_URL).permitAll()
                        // /saml2/authenticate/{registrationId} initiates the
                        // AuthnRequest redirect; /login/saml2/sso/{registrationId}
                        // is the ACS endpoint the IdP posts the assertion back
                        // to. Both must be reachable by an anonymous principal —
                        // that's how SAML authentication happens in the first
                        // place. /saml2/service-provider-metadata/{registrationId}
                        // is included too: SP metadata is conventionally public,
                        // even though this setup doesn't rely on Keycloak fetching
                        // it (the realm's client was configured with static values).
                        .requestMatchers("/saml2/**", "/login/saml2/**").permitAll()
                        // A principal mid-MFA-gate holds FACTOR_PASSWORD but not
                        // yet FACTOR_OTT. Using the DSL's own hasAuthority(...)
                        // here would go through the composed
                        // AuthorizationManagerFactory (see
                        // AuthorizeHttpRequestsConfigurer.AuthorizedUrl /
                        // DefaultAuthorizationManagerFactory), which ANDs in the
                        // same missing-FACTOR_OTT requirement via
                        // setAdditionalAuthorization(...) — permitAll(),
                        // denyAll() and anonymous() are the only factory methods
                        // exempt from that composition. So these three OTT
                        // endpoints are wired with access(...) instead, handing
                        // it a plain AuthorityAuthorizationManager built by hand
                        // rather than through the factory: it still requires a
                        // real, password-authenticated principal (not
                        // anonymous, not permitAll), but doesn't re-demand the
                        // very factor these pages exist to obtain.
                        .requestMatchers(
                                EmailOttDeliveryHandler.OTT_INPUT_URL,
                                EmailOttDeliveryHandler.OTT_REQUEST_URL,
                                GenerateOneTimeTokenFilter.DEFAULT_GENERATE_URL)
                        .access(AuthorityAuthorizationManager.hasAuthority(FactorGrantedAuthority.PASSWORD_AUTHORITY))
                        .anyRequest().authenticated())
                .with(formLoginConfigurer, Customizer.withDefaults())
                .with(oneTimeTokenConfigurer, Customizer.withDefaults())
                // Applied last, deliberately — see Saml2Configurer's own
                // javadoc: DelegatingAuthenticationEntryPoint picks the FIRST
                // registered match, so form login's broad
                // NegatedRequestMatcher(BEARER_TOKEN_MATCHER) entry point
                // (registered above) keeps winning for ordinary unauthenticated
                // requests regardless of what SAML registers.
                .with(saml2Configurer, Customizer.withDefaults());

        return http.build();
    }
}
