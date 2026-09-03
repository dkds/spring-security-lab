import type { ReactNode } from 'react';
import { useAuth } from 'react-oidc-context';

interface RequireAuthProps {
  children: ReactNode;
}

/**
 * Protected route component.
 * Initiates the OAuth2 redirect if the user is not authenticated.
 * react-oidc-context saves the current location and restores it after login.
 */
function RequireAuth({ children }: RequireAuthProps) {
  const auth = useAuth();

  if (auth.isLoading) {
    return <div className="loading">Loading authentication...</div>;
  }

  if (!auth.isAuthenticated) {
    // Initiate PKCE flow directly — no intermediate /login page needed.
    auth.signinRedirect();
    return null;
  }

  return <>{children}</>;
}

export default RequireAuth;
