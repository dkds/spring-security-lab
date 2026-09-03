import { useAuth } from 'react-oidc-context';

/**
 * Custom hook for accessing authentication state and actions.
 * Wraps react-oidc-context's useAuth() hook to provide a stable API surface
 * that can evolve independently of the OIDC library.
 */
export function useAppAuth() {
  const auth = useAuth();

  return {
    // Auth state
    isAuthenticated: auth.isAuthenticated,
    isLoading: auth.isLoading,
    error: auth.error,
    user: auth.user,

    // Token access
    accessToken: auth.user?.access_token,
    idToken: auth.user?.id_token,

    // Methods
    login: () => auth.signinRedirect(),
    logout: () => auth.signoutRedirect(),
    refresh: () => auth.signinSilent(),
  };
}
