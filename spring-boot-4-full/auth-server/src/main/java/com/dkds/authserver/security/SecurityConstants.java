package com.dkds.authserver.security;

/// Constants for security configuration.
public class SecurityConstants {
    public static final String ROLE_MEMBER = "MEMBER";
    public static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String ROLE_PREFIX = "ROLE_";

    // Authority prefixes (DESIGN.md: three disjoint namespaces)
    public static final String PERM_PREFIX = "PERM_";
    public static final String FACTOR_PREFIX = "FACTOR_";

    // Factor authorities
    public static final String FACTOR_PASSWORD = "FACTOR_PASSWORD";
    public static final String FACTOR_OTT = "FACTOR_OTT";
    public static final String FACTOR_SAML = "FACTOR_SAML";

    // API paths
    public static final String API_MATCHER = "/api/**";
    public static final String OAUTH2_AUTHORIZE_MATCHER = "/oauth2/authorize";
    public static final String LOGIN_PAGE = "/login";
    public static final String LOGIN_PROCESSING_URL = "/login";

    /// Fallback landing page after a successful form login when there is no
    /// saved request to resume (SavedRequestAwareAuthenticationSuccessHandler
    /// otherwise falls back to its own default of "/", which this
    /// authorization-server-only app never maps — see FormLoginConfigurer.
    public static final String LOGIN_SUCCESS_URL = "/login-success";

    // Session
    public static final long SESSION_TIMEOUT_MINUTES = 30;
}
