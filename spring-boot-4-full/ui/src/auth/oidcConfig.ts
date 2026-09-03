import type { AuthProviderProps } from 'react-oidc-context';
import { WebStorageStateStore } from 'oidc-client-ts';

/**
 * OIDC/OAuth2 configuration for react-oidc-context.
 * Configures the SPA client for Authorization Code + PKCE flow.
 * PKCE is handled automatically by oidc-client-ts (disablePKCE defaults to false).
 */
export const oidcConfig: AuthProviderProps = {
  authority: import.meta.env.VITE_AUTH_ISSUER ?? 'http://localhost:9000',
  client_id: import.meta.env.VITE_CLIENT_ID ?? 'spa-client',
  redirect_uri: import.meta.env.VITE_REDIRECT_URI ?? 'http://localhost:5173/callback',
  scope: 'openid profile email',
  automaticSilentRenew: true,
  silentRequestTimeoutInSeconds: 10,
  loadUserInfo: true,
  stateStore: new WebStorageStateStore({ store: window.localStorage }),
  userStore: new WebStorageStateStore({ store: window.localStorage }),
};
