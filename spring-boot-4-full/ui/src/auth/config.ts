/**
 * OAuth2 / OIDC configuration for the portal-ui client.
 *
 * The auth-server acts as the Authorization Server (Spring Authorization Server).
 * All values can be overridden via environment variables (see .env.example).
 */
export const AUTH_CONFIG = {
  /** Base URL of the Spring Authorization Server */
  issuer: import.meta.env.VITE_AUTH_ISSUER ?? 'http://localhost:9000',

  /** OAuth2 client_id registered on the auth-server */
  clientId: import.meta.env.VITE_CLIENT_ID ?? 'portal-ui',

  /** Absolute URL this app is allowed to receive the authorization code at */
  redirectUri: import.meta.env.VITE_REDIRECT_URI ?? 'http://localhost:5173/callback',

  /** OAuth2 scopes to request */
  scope: 'openid profile email',

  /** Authorization endpoint */
  get authorizationEndpoint() {
    return `${this.issuer}/oauth2/authorize`
  },

  /** Token endpoint */
  get tokenEndpoint() {
    return `${this.issuer}/oauth2/token`
  },

  /** End-session endpoint */
  get endSessionEndpoint() {
    return `${this.issuer}/connect/logout`
  },
} as const
